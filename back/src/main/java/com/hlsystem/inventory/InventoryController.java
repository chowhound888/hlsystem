package com.hlsystem.inventory;

import static com.hlsystem.common.Values.*;

import com.hlsystem.common.ApiResponse;
import com.hlsystem.security.AccessService;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
  private final InventoryService service;
  private final AccessService access;
  private final JdbcTemplate db;

  public InventoryController(InventoryService service, AccessService access, JdbcTemplate db) {
    this.service = service;
    this.access = access;
    this.db = db;
  }

  @GetMapping("/inventory/stocks")
  ApiResponse<?> stocks(
      @RequestParam long locationId, @RequestParam(defaultValue = "") String keyword) {
    access.require("inventory:read");
    return ApiResponse.ok(service.stocks(locationId, keyword));
  }

  @GetMapping("/inventory/documents")
  ApiResponse<?> documents(@RequestParam long locationId) {
    access.require("inventory:read");
    return ApiResponse.ok(service.documents(locationId));
  }

  @GetMapping("/inventory/documents/{id}")
  ApiResponse<?> detail(@PathVariable long id) {
    access.require("inventory:read");
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping("/inventory/documents")
  ApiResponse<?> create(@RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", service.create(r)));
  }

  @PostMapping("/inventory/documents/{id}/{action}")
  ApiResponse<?> action(
      @PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> r) {
    service.action(id, action, Integer.parseInt(r.get("version").toString()), text(r, "reason"));
    return ApiResponse.ok(null);
  }

  @GetMapping("/inventory/ledger")
  ApiResponse<?> ledger(@RequestParam long locationId) {
    access.require("inventory:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT l.*,i.name,b.batch_no,u.name unit,d.number,d.type,p.display_name operator FROM"
                + " inv_ledger l JOIN inv_stock s ON s.id=l.stock_id JOIN inv_cost_layer c ON"
                + " c.id=s.cost_layer_id JOIN inv_batch b ON b.id=c.batch_id JOIN md_item i ON"
                + " i.id=b.item_id JOIN md_unit u ON u.id=i.base_unit_id JOIN inv_document_item x"
                + " ON x.id=l.document_item_id JOIN inv_document d ON d.id=x.document_id JOIN"
                + " sys_user p ON p.id=l.operator_id WHERE s.location_id=? ORDER BY l.occurred_at"
                + " DESC,l.id DESC LIMIT 500",
            locationId));
  }

  @GetMapping("/workbench")
  ApiResponse<?> workbench(@RequestParam long locationId) {
    access.location(locationId);
    var alerts =
        db.queryForList(
            "SELECT s.id,i.name,b.batch_no,b.expires_on,CASE WHEN b.expires_on<CURDATE() THEN '已过期'"
                + " WHEN b.expires_on<=DATE_ADD(CURDATE(),INTERVAL 90 DAY) THEN '近效期' ELSE '低库存'"
                + " END reason FROM inv_stock s JOIN inv_cost_layer c ON c.id=s.cost_layer_id JOIN"
                + " inv_batch b ON b.id=c.batch_id JOIN md_item i ON i.id=b.item_id WHERE"
                + " s.location_id=? AND s.on_hand_qty>0 AND"
                + " (b.expires_on<=DATE_ADD(CURDATE(),INTERVAL 90 DAY) OR s.on_hand_qty<20) ORDER"
                + " BY b.expires_on LIMIT 20",
            locationId);
    return ApiResponse.ok(
        Map.of(
            "drugCount",
            db.queryForObject("SELECT COUNT(*) FROM md_drug", Long.class),
            "inventoryCost",
            db.queryForObject(
                "SELECT COALESCE(SUM(remaining_cost),0) FROM inv_stock WHERE location_id=?",
                java.math.BigDecimal.class,
                locationId),
            "pendingCount",
            db.queryForObject(
                "SELECT COUNT(*) FROM inv_document WHERE (location_id=? OR target_location_id=?)"
                    + " AND status IN ('SUBMITTED','APPROVED','SHIPPED')",
                Long.class,
                locationId,
                locationId),
            "alertCount",
            alerts.size(),
            "alerts",
            alerts,
            "recentDocuments",
            service.documents(locationId).stream().limit(8).toList()));
  }
}
