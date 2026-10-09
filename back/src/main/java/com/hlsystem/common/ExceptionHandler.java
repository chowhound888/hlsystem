package com.hlsystem.common;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ExceptionHandler {
  @org.springframework.web.bind.annotation.ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException ex) {
    return ResponseEntity.status(ex.status).body(new ApiResponse<>(ex.code, ex.getMessage(), null));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
    return ResponseEntity.badRequest()
        .body(
            new ApiResponse<>(
                "VALIDATION_FAILED",
                "请检查填写内容",
                ex.getBindingResult().getFieldErrors().stream()
                    .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                    .toList()));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> constraint(DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiResponse<>("CONSTRAINT_CONFLICT", "编码重复、关联数据不存在或记录已被使用", null));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception ex) {
    log.error("Unhandled request error", ex);
    return ResponseEntity.internalServerError()
        .body(new ApiResponse<>("INTERNAL_ERROR", "操作未完成，请稍后重试", null));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalidInput(Exception ex) {
    return ResponseEntity.badRequest()
        .body(new ApiResponse<>("VALIDATION_FAILED", "参数格式不正确，请检查数量、日期及关联编号", null));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(
      org.springframework.dao.EmptyResultDataAccessException.class)
  ResponseEntity<?> missing(org.springframework.dao.EmptyResultDataAccessException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiResponse<>("NOT_FOUND", "记录不存在或已失效", null));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(
      org.springframework.security.access.AccessDeniedException.class)
  ResponseEntity<?> denied(org.springframework.security.access.AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiResponse<>("FORBIDDEN", "没有此操作权限", null));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(
      org.springframework.dao.CannotAcquireLockException.class)
  ResponseEntity<?> lock(org.springframework.dao.CannotAcquireLockException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiResponse<>("RETRYABLE_CONFLICT", "业务数据正在被其他操作更新，请重试", null));
  }
}
