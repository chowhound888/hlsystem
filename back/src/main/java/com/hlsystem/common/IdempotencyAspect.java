package com.hlsystem.common;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hlsystem.security.AccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Component
public class IdempotencyAspect {
  private final HttpServletRequest request;
  private final JdbcTemplate db;
  private final AccessService access;
  private final ObjectMapper json;
  private final TransactionTemplate tx;

  public IdempotencyAspect(
      HttpServletRequest request,
      JdbcTemplate db,
      AccessService access,
      ObjectMapper json,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.request = request;
    this.db = db;
    this.access = access;
    this.json = json;
    this.tx = new TransactionTemplate(manager);
    this.tx.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Around(
      "within(@org.springframework.web.bind.annotation.RestController *) && execution(*"
          + " com.hlsystem..*(..))")
  public Object protect(ProceedingJoinPoint point) throws Throwable {
    if (!"POST".equals(request.getMethod())) return point.proceed();
    String key = request.getHeader("Idempotency-Key"), route = request.getRequestURI();
    if (route.startsWith("/api/v1/auth/")) return point.proceed();
    boolean critical =
        route.matches(
            ".*(/execute|/receive|/pay|/refund|/deposits|/dispense|/confirm|/settle|/issue|/process|/test|/returns|/generate)$");
    if (key == null) {
      if (critical) throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "请提供幂等请求标识");
      return point.proceed();
    }
    if (key.length() > 100 || key.isBlank())
      throw new BusinessException("VALIDATION_FAILED", "请求标识无效");
    long user = access.current().id();
    String hash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        json.writeValueAsString(point.getArgs()).getBytes(StandardCharsets.UTF_8)));
    return tx.execute(
        status -> {
          try {
            try {
              db.update(
                  "INSERT INTO"
                      + " sys_idempotency(id,user_id,route_key,request_key,request_hash,result_json,status)"
                      + " VALUES (?,?,?,?,?,'{}','PENDING')",
                  IdWorker.getId(),
                  user,
                  route,
                  key,
                  hash);
            } catch (DuplicateKeyException duplicate) {
              var previous =
                  db.queryForMap(
                      "SELECT * FROM sys_idempotency WHERE user_id=? AND route_key=? AND"
                          + " request_key=? FOR UPDATE",
                      user,
                      route,
                      key);
              if (!previous.get("request_hash").equals(hash))
                throw new BusinessException(
                    "DUPLICATE_OPERATION", "同一请求标识不能用于不同内容", HttpStatus.CONFLICT);
              return json.readValue(previous.get("result_json").toString(), ApiResponse.class);
            }
            Object result = point.proceed();
            db.update(
                "UPDATE sys_idempotency SET result_json=?,status='COMPLETED' WHERE user_id=? AND"
                    + " route_key=? AND request_key=?",
                json.writeValueAsString(result),
                user,
                route,
                key);
            return result;
          } catch (RuntimeException e) {
            throw e;
          } catch (Throwable e) {
            throw new IllegalStateException(e);
          }
        });
  }
}
