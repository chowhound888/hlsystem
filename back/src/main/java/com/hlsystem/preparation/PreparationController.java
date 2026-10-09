package com.hlsystem.preparation;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.ApiResponse;
import com.hlsystem.common.BusinessException;
import com.hlsystem.common.Values;
import com.hlsystem.security.AccessService;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/preparation")
public class PreparationController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final PreparationService service;

  public PreparationController(JdbcTemplate db, AccessService access, PreparationService service) {
    this.db = db;
    this.access = access;
    this.service = service;
  }

  @GetMapping("/formulas")
  ApiResponse<?> formulas() {
    access.require("preparation:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT f.*,i.name outputName FROM prep_formula f JOIN md_item i ON"
                + " i.id=f.output_item_id ORDER BY f.code"));
  }

  @PostMapping("/formulas")
  ApiResponse<?> formula(@RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", service.formula(r)));
  }

  @GetMapping("/orders")
  ApiResponse<?> orders(@RequestParam long locationId) {
    access.require("preparation:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT p.*,f.name formulaName,i.name outputName FROM prep_production_order p JOIN"
                + " prep_formula f ON f.id=p.formula_id JOIN md_item i ON i.id=f.output_item_id"
                + " WHERE p.location_id=? ORDER BY p.created_at DESC",
            locationId));
  }

  @PostMapping("/orders")
  ApiResponse<?> create(@RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", service.create(r)));
  }

  @GetMapping("/orders/{id}")
  ApiResponse<?> detail(@PathVariable long id) {
    access.require("preparation:read");
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping("/orders/{id}/{action}")
  ApiResponse<?> action(
      @PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> r) {
    service.action(id, action, r);
    return ApiResponse.ok(null);
  }

  @GetMapping("/raw-quality")
  ApiResponse<?> raw(@RequestParam long locationId) {
    access.require("preparation:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT s.*,i.name,b.batch_no FROM inv_stock s JOIN inv_cost_layer c ON"
                + " c.id=s.cost_layer_id JOIN inv_batch b ON b.id=c.batch_id JOIN md_item i ON"
                + " i.id=b.item_id WHERE s.location_id=? AND s.stock_status='PENDING_QC' AND"
                + " s.on_hand_qty>0 ORDER BY i.code",
            locationId));
  }

  @GetMapping("/raw-tests")
  ApiResponse<?> rawTests(@RequestParam long locationId) {
    access.require("preparation:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT t.*,i.name,b.batch_no,u.display_name tester FROM prep_quality_test t JOIN"
                + " inv_stock s ON s.id=t.stock_id JOIN inv_cost_layer c ON c.id=s.cost_layer_id"
                + " JOIN inv_batch b ON b.id=c.batch_id JOIN md_item i ON i.id=b.item_id JOIN"
                + " sys_user u ON u.id=t.tested_by WHERE s.location_id=? ORDER BY t.tested_at DESC",
            locationId));
  }

  @PostMapping("/raw-tests")
  @Transactional
  ApiResponse<?> testRaw(@RequestBody Map<String, Object> r) {
    access.require("preparation:write");
    var stock =
        db.queryForMap(
            "SELECT * FROM inv_stock WHERE id=? FOR UPDATE", Values.id(r.get("stockId")));
    access.location(Values.id(stock.get("location_id")));
    if (!stock.get("stock_status").equals("PENDING_QC"))
      throw new BusinessException("INVALID_STATE", "该原料不是待检库存");
    String conclusion = Values.text(r, "conclusion");
    if (!java.util.Set.of("PASSED", "FAILED").contains(conclusion)
        || Values.text(r, "standard").isBlank()
        || Values.text(r, "measured").isBlank())
      throw new BusinessException("VALIDATION_FAILED", "请填写完整原料质检信息");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " prep_quality_test(id,stock_id,type,standard_description,measured_value,conclusion,tested_by)"
            + " VALUES (?,?,'RAW',?,?,?,?)",
        id,
        stock.get("id"),
        Values.text(r, "standard"),
        Values.text(r, "measured"),
        conclusion,
        access.current().id());
    return ApiResponse.ok(Map.of("id", id));
  }

  @PostMapping("/raw-tests/{id}/confirm")
  @Transactional
  ApiResponse<?> confirmRaw(@PathVariable long id) {
    access.require("preparation:write");
    service.confirmRaw(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/sales")
  ApiResponse<?> sales(@RequestParam long locationId) {
    access.require("preparation:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT s.*,i.name FROM prep_sales_order s JOIN md_item i ON i.id=s.item_id WHERE"
                + " s.location_id=? ORDER BY s.created_at DESC",
            locationId));
  }

  @PostMapping("/sales")
  ApiResponse<?> sale(@RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", service.sale(r)));
  }

  @PostMapping("/sales/{id}/execute")
  ApiResponse<?> executeSale(@PathVariable long id) {
    service.executeSale(id);
    return ApiResponse.ok(null);
  }
}
