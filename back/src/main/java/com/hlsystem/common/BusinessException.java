package com.hlsystem.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  public final String code;
  public final HttpStatus status;

  public BusinessException(String code, String message) {
    this(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public BusinessException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}
