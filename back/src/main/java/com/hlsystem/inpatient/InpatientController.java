package com.hlsystem.inpatient;

import static com.hlsystem.common.Values.*;

import com.hlsystem.common.ApiResponse;
import com.hlsystem.security.AccessService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inpatient")
public class InpatientController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final InpatientService service;

  public InpatientController(JdbcTemplate db, AccessService access, InpatientService service) {
    this.db = db;
    this.access = access;
    this.service = service;
  }

  @GetMapping("/orders")
  ApiResponse<?> orders(@RequestParam long locationId) {
    if (!access.current().permissions().contains("inpatient:read"))
      access.require("clinical:write");
    access.location(locationId);
    String sql =
        "SELECT o.*,p.name patientName,e.bed_no,i.name drugName,i.specification,u.name unit FROM"
            + " ip_medical_order o JOIN cl_encounter e ON e.id=o.encounter_id JOIN cl_patient p ON"
            + " p.id=e.patient_id JOIN md_item i ON i.id=o.item_id JOIN md_unit u ON"
            + " u.id=i.base_unit_id WHERE o.location_id=?";
    boolean doctor =
        access.current().roles().contains("DOCTOR") && !access.current().roles().contains("ADMIN");
    return ApiResponse.ok(
        doctor
            ? db.queryForList(
                sql + " AND o.doctor_id=? ORDER BY o.created_at DESC LIMIT 200",
                locationId,
                access.current().id())
            : db.queryForList(sql + " ORDER BY o.created_at DESC LIMIT 200", locationId));
  }

  @PostMapping("/orders")
  ApiResponse<?> create(@RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", service.create(r)));
  }

  @PostMapping("/orders/{id}/stop")
  ApiResponse<?> stop(@PathVariable long id) {
    service.stop(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/executions")
  ApiResponse<?> executions(@RequestParam long locationId, @RequestParam String date) {
    access.require("inpatient:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT x.*,o.base_qty,o.dose,o.dose_unit,o.route,o.order_type,p.name"
                + " patientName,e.bed_no,i.name drugName,d.dosage_form FROM ip_order_execution x"
                + " JOIN ip_medical_order o ON o.id=x.order_id JOIN cl_encounter e ON"
                + " e.id=o.encounter_id JOIN cl_patient p ON p.id=e.patient_id JOIN md_item i ON"
                + " i.id=o.item_id JOIN md_drug d ON d.item_id=i.id WHERE o.location_id=? AND"
                + " DATE(x.scheduled_at)=? ORDER BY e.bed_no,x.scheduled_at",
            locationId,
            LocalDate.parse(date)));
  }

  @PostMapping("/executions/generate")
  ApiResponse<?> generate(@RequestBody Map<String, Object> r) {
    service.generate(id(r.get("locationId")), LocalDate.parse(text(r, "date")));
    return ApiResponse.ok(null);
  }

  @PostMapping("/executions/{id}/{action}")
  ApiResponse<?> action(
      @PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> r) {
    service.action(id, action, text(r, "reason"));
    return ApiResponse.ok(null);
  }
}
