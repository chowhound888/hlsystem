package com.hlsystem.outpatient;

import static com.hlsystem.common.Values.*;

import com.hlsystem.common.ApiResponse;
import com.hlsystem.security.AccessService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/outpatient")
public class OutpatientController {
  private final OutpatientService service;
  private final AccessService access;

  public OutpatientController(OutpatientService service, AccessService access) {
    this.service = service;
    this.access = access;
  }

  private void read() {
    if (!access.current().permissions().contains("outpatient:read"))
      access.require("clinical:write");
  }

  @GetMapping("/prescriptions")
  ApiResponse<?> list(@RequestParam long locationId) {
    read();
    return ApiResponse.ok(service.list(locationId));
  }

  @GetMapping("/prescriptions/{id}")
  ApiResponse<?> detail(@PathVariable long id) {
    read();
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping("/prescriptions")
  ApiResponse<?> create(@RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", service.create(r)));
  }

  @PostMapping("/prescriptions/{id}/check")
  ApiResponse<?> check(@PathVariable long id) {
    read();
    return ApiResponse.ok(service.check(id));
  }

  @PostMapping("/prescriptions/{id}/{action}")
  ApiResponse<?> action(
      @PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> r) {
    service.action(id, action, Integer.parseInt(r.get("version").toString()), text(r, "reason"));
    return ApiResponse.ok(null);
  }

  @PostMapping("/prescriptions/{id}/returns")
  ApiResponse<?> returns(@PathVariable long id, @RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", service.returnDrug(id, r)));
  }

  @PostMapping("/returns/{id}/refund")
  ApiResponse<?> refund(@PathVariable long id) {
    service.refund(id);
    return ApiResponse.ok(null);
  }
}
