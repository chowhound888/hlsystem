package com.hlsystem.inventory;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/requisitions")
public class RequisitionController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final InventoryService inventory;
  private final AuditService audit;

  public RequisitionController(
      JdbcTemplate db, AccessService access, InventoryService inventory, AuditService audit) {
    this.db = db;
    this.access = access;
    this.inventory = inventory;
    this.audit = audit;
  }

  @GetMapping
  ApiResponse<?> list(@RequestParam long locationId) {
    access.require("inventory:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT r.*,l.name requestLocation,s.name supplyLocation,u.display_name creator FROM"
                + " inv_requisition r JOIN sys_location l ON l.id=r.location_id JOIN sys_location s"
                + " ON s.id=r.supply_location_id JOIN sys_user u ON u.id=r.created_by WHERE"
                + " r.location_id=? OR r.supply_location_id=? ORDER BY r.created_at DESC",
            locationId,
            locationId));
  }

  @GetMapping("/{id}")
  ApiResponse<?> detail(@PathVariable long id) {
    access.require("inventory:read");
    var r = db.queryForMap("SELECT * FROM inv_requisition WHERE id=?", id);
    if (!access.current().locationIds().contains(id(r.get("location_id"))))
      access.location(id(r.get("supply_location_id")));
    return ApiResponse.ok(
        Map.of(
            "header",
            r,
            "items",
            db.queryForList(
                "SELECT x.*,i.name,u.name unit,i.base_unit_id FROM inv_requisition_item x JOIN"
                    + " md_item i ON i.id=x.item_id JOIN md_unit u ON u.id=i.base_unit_id WHERE"
                    + " requisition_id=?",
                id)));
  }

  @PostMapping
  @Transactional
  ApiResponse<?> create(@RequestBody Map<String, Object> r) {
    access.require("inventory:write");
    long location = id(r.get("locationId")), supply = id(r.get("supplyLocationId"));
    access.location(location);
    if (location == supply) throw new BusinessException("VALIDATION_FAILED", "来源与申请机构不能相同");
    if (!(r.get("items") instanceof List<?> items) || items.isEmpty())
      throw new BusinessException("VALIDATION_FAILED", "请填写领药明细");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO inv_requisition(id,number,location_id,supply_location_id,created_by) VALUES"
            + " (?,?,?,?,?)",
        id,
        "REQ" + id,
        location,
        supply,
        access.current().id());
    for (Object item : items) {
      var x = (Map<?, ?>) item;
      var qty = decimal(x.get("quantity"));
      positive(qty, "申请数量");
      db.update(
          "INSERT INTO inv_requisition_item VALUES (?,?,?,?)",
          IdWorker.getId(),
          id,
          id(x.get("itemId")),
          qty);
    }
    audit.record("CREATE", "REQUISITION", id, null);
    return ApiResponse.ok(Map.of("id", id));
  }

  @PostMapping("/{id}/fulfill")
  @Transactional
  ApiResponse<?> fulfill(@PathVariable long id) {
    access.require("inventory:write");
    var r = db.queryForMap("SELECT * FROM inv_requisition WHERE id=? FOR UPDATE", id);
    access.location(id(r.get("supply_location_id")));
    InventoryService.requireState(text(r, "status"), "SUBMITTED");
    var lines = new ArrayList<Map<String, Object>>();
    for (var x :
        db.queryForList(
            "SELECT x.*,i.base_unit_id FROM inv_requisition_item x JOIN md_item i ON i.id=x.item_id"
                + " WHERE requisition_id=?",
            id))
      lines.add(
          Map.of(
              "itemId",
              x.get("item_id"),
              "unitId",
              x.get("base_unit_id"),
              "quantity",
              x.get("base_qty")));
    long doc =
        inventory.create(
            Map.of(
                "type",
                "TRANSFER",
                "locationId",
                r.get("supply_location_id"),
                "targetLocationId",
                r.get("location_id"),
                "reason",
                "领药申请 " + r.get("number"),
                "items",
                lines));
    db.update("UPDATE inv_requisition SET status='FULFILLED' WHERE id=?", id);
    audit.record("FULFILL", "REQUISITION", id, null);
    return ApiResponse.ok(Map.of("documentId", doc));
  }
}
