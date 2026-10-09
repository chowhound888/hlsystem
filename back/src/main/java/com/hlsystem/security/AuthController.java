package com.hlsystem.security;

import com.hlsystem.common.ApiResponse;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  private final AccessService access;
  private final JdbcTemplate db;

  public AuthController(AccessService access, JdbcTemplate db) {
    this.access = access;
    this.db = db;
  }

  @GetMapping("/api/v1/auth/csrf")
  ApiResponse<?> csrf(CsrfToken token) {
    return ApiResponse.ok(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
  }

  @GetMapping("/api/v1/auth/me")
  ApiResponse<?> me() {
    var u = access.current();
    return ApiResponse.ok(
        Map.of(
            "id",
            u.id(),
            "username",
            u.username(),
            "displayName",
            u.displayName(),
            "roles",
            u.roles(),
            "permissions",
            u.permissions(),
            "locations",
            u.locationIds().stream()
                .map(id -> db.queryForMap("SELECT id,name,type FROM sys_location WHERE id=?", id))
                .toList()));
  }

  @GetMapping("/api/v1/health")
  ApiResponse<?> health() {
    return ApiResponse.ok(
        Map.of("status", "UP", "database", db.queryForObject("SELECT VERSION()", String.class)));
  }
}
