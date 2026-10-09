package com.hlsystem.system;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system")
public class UserManagementController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final PasswordEncoder encoder;
  private final AuditService audit;

  public UserManagementController(
      JdbcTemplate db, AccessService access, PasswordEncoder encoder, AuditService audit) {
    this.db = db;
    this.access = access;
    this.encoder = encoder;
    this.audit = audit;
  }

  @GetMapping("/roles")
  ApiResponse<?> roles() {
    access.require("system:read");
    return ApiResponse.ok(db.queryForList("SELECT * FROM sys_role ORDER BY id"));
  }

  @PostMapping("/users")
  @Transactional
  ApiResponse<?> create(@RequestBody Map<String, Object> r) {
    access.require("system:write");
    String username = text(r, "username"),
        name = text(r, "displayName"),
        password = text(r, "password");
    if (!username.matches("[a-zA-Z0-9_-]{3,64}") || name.isBlank() || password.length() < 6)
      throw new BusinessException("VALIDATION_FAILED", "用户名需3至64位英文数字，姓名必填，密码至少6位");
    long user = IdWorker.getId();
    db.update(
        "INSERT INTO sys_user(id,username,password_hash,display_name,department_id) VALUES"
            + " (?,?,?,?,?)",
        user,
        username.toLowerCase(Locale.ROOT),
        encoder.encode(password),
        name,
        id(r.get("departmentId")));
    long role = id(r.get("roleId"));
    db.update("INSERT INTO sys_user_role VALUES (?,?)", user, role);
    if (!(r.get("locationIds") instanceof List<?> scopes) || scopes.isEmpty())
      throw new BusinessException("VALIDATION_FAILED", "至少授予一个机构范围");
    for (Object scope : scopes)
      db.update("INSERT INTO sys_user_scope VALUES (?,?)", user, id(scope));
    audit.record("CREATE", "USER", user, null);
    return ApiResponse.ok(Map.of("id", user));
  }

  @PostMapping("/users/{id}/enabled")
  @Transactional
  ApiResponse<?> enabled(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("system:write");
    if (id == access.current().id()) throw new BusinessException("VALIDATION_FAILED", "不能停用当前账号");
    boolean enabled = Boolean.TRUE.equals(r.get("enabled"));
    if (db.update("UPDATE sys_user SET enabled=? WHERE id=?", enabled, id) != 1)
      throw new BusinessException("NOT_FOUND", "账号不存在");
    String username =
        db.queryForObject("SELECT username FROM sys_user WHERE id=?", String.class, id);
    db.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", username);
    audit.record(enabled ? "ENABLE" : "DISABLE", "USER", id, null);
    return ApiResponse.ok(null);
  }

  @PostMapping("/users/{id}/reset-password")
  @Transactional
  ApiResponse<?> reset(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("system:write");
    String password = text(r, "password");
    if (password.length() < 6) throw new BusinessException("VALIDATION_FAILED", "密码至少6位");
    db.update("UPDATE sys_user SET password_hash=? WHERE id=?", encoder.encode(password), id);
    String username =
        db.queryForObject("SELECT username FROM sys_user WHERE id=?", String.class, id);
    db.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", username);
    audit.record("RESET_PASSWORD", "USER", id, null);
    return ApiResponse.ok(null);
  }

  @PostMapping("/locations")
  @Transactional
  ApiResponse<?> location(@RequestBody Map<String, Object> r) {
    access.require("system:write");
    String type = text(r, "type");
    if (!Set.of("WAREHOUSE", "OUTPATIENT", "INPATIENT", "WARD", "PREPARATION").contains(type)
        || text(r, "name").isBlank()
        || text(r, "code").isBlank()) throw new BusinessException("VALIDATION_FAILED", "请填写有效机构资料");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO sys_location(id,department_id,code,name,type) VALUES (?,?,?,?,?)",
        id,
        id(r.get("departmentId")),
        text(r, "code"),
        text(r, "name"),
        type);
    for (Long user :
        db.queryForList(
            "SELECT DISTINCT ur.user_id FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id"
                + " WHERE r.code IN ('ADMIN','ACCOUNTANT')",
            Long.class)) db.update("INSERT INTO sys_user_scope VALUES (?,?)", user, id);
    audit.record("CREATE", "LOCATION", id, null);
    return ApiResponse.ok(Map.of("id", id));
  }

  @PostMapping("/departments")
  @Transactional
  ApiResponse<?> department(@RequestBody Map<String, Object> r) {
    access.require("system:write");
    if (text(r, "name").isBlank() || text(r, "code").isBlank())
      throw new BusinessException("VALIDATION_FAILED", "请填写科室编码和名称");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO sys_department(id,code,name,type) VALUES (?,?,?,?)",
        id,
        text(r, "code"),
        text(r, "name"),
        text(r, "type").isBlank() ? "CLINICAL" : text(r, "type"));
    audit.record("CREATE", "DEPARTMENT", id, null);
    return ApiResponse.ok(Map.of("id", id));
  }
}
