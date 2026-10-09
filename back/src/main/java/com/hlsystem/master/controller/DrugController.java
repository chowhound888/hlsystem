package com.hlsystem.master.controller;

import com.hlsystem.common.ApiResponse;
import com.hlsystem.master.dto.DrugRequest;
import com.hlsystem.master.service.DrugService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/master/drugs")
public class DrugController {
  private final DrugService service;

  public DrugController(DrugService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('master:read')")
  ApiResponse<?> list(
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.ok(service.list(keyword, page, pageSize));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('master:read')")
  ApiResponse<?> detail(@PathVariable long id) {
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('master:write')")
  ApiResponse<?> create(@Valid @RequestBody DrugRequest r) {
    return ApiResponse.ok(Map.of("id", service.create(r)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('master:write')")
  ApiResponse<?> update(@PathVariable long id, @Valid @RequestBody DrugRequest r) {
    service.update(id, r);
    return ApiResponse.ok(null);
  }

  @PostMapping("/{id}/enabled")
  @PreAuthorize("hasAuthority('master:write')")
  ApiResponse<?> enabled(@PathVariable long id, @RequestBody Map<String, Boolean> r) {
    service.enabled(id, Boolean.TRUE.equals(r.get("enabled")));
    return ApiResponse.ok(null);
  }
}
