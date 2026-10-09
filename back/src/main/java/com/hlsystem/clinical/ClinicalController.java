package com.hlsystem.clinical;

import static com.hlsystem.common.Values.*;

import com.hlsystem.common.ApiResponse;
import com.hlsystem.security.AccessService;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clinical")
public class ClinicalController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final ClinicalService service;

  public ClinicalController(JdbcTemplate db, AccessService access, ClinicalService service) {
    this.db = db;
    this.access = access;
    this.service = service;
  }

  @GetMapping("/patients")
  ApiResponse<?> patients(@RequestParam(defaultValue = "") String keyword) {
    access.require("clinical:read");
    String like = "%" + keyword + "%";
    return ApiResponse.ok(
        db.queryForList(
            "SELECT * FROM cl_patient WHERE name LIKE ? OR patient_no LIKE ? ORDER BY created_at"
                + " DESC LIMIT 200",
            like,
            like));
  }

  @PostMapping("/patients")
  ApiResponse<?> patient(@RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", service.patient(r)));
  }

  @PutMapping("/patients/{id}")
  ApiResponse<?> updatePatient(@PathVariable long id, @RequestBody Map<String, Object> r) {
    service.updatePatient(id, r);
    return ApiResponse.ok(null);
  }

  @GetMapping("/encounters")
  ApiResponse<?> encounters(@RequestParam(defaultValue = "") String type) {
    access.require("clinical:read");
    boolean doctor =
        access.current().roles().contains("DOCTOR") && !access.current().roles().contains("ADMIN");
    String sql =
        "SELECT e.*,p.name patientName,d.name department,a.balance FROM cl_encounter e JOIN"
            + " cl_patient p ON p.id=e.patient_id JOIN sys_department d ON d.id=e.department_id"
            + " LEFT JOIN cl_deposit_account a ON a.encounter_id=e.id WHERE (?='' OR e.type=?)";
    return ApiResponse.ok(
        doctor
            ? db.queryForList(
                sql + " AND e.department_id=? ORDER BY e.admitted_at DESC LIMIT 200",
                type,
                type,
                access.current().departmentId())
            : db.queryForList(sql + " ORDER BY e.admitted_at DESC LIMIT 200", type, type));
  }

  @PostMapping("/encounters")
  ApiResponse<?> encounter(@RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", service.encounter(r)));
  }

  @PostMapping("/encounters/{id}/deposits")
  ApiResponse<?> deposit(@PathVariable long id, @RequestBody Map<String, Object> r) {
    service.deposit(id, decimal(r.get("amount")));
    return ApiResponse.ok(null);
  }

  @GetMapping("/payments")
  ApiResponse<?> payments(@RequestParam long encounterId) {
    access.require("clinical:read");
    service.encounterAccess(encounterId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT * FROM cl_payment WHERE encounter_id=? ORDER BY paid_at DESC", encounterId));
  }
}
