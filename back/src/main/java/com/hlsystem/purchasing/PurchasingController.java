package com.hlsystem.purchasing;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.*;
import com.hlsystem.inventory.InventoryService;
import com.hlsystem.security.AccessService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchasing")
public class PurchasingController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final AuditService audit;
  private final InventoryService inventory;

  public PurchasingController(
      JdbcTemplate db, AccessService access, AuditService audit, InventoryService inventory) {
    this.db = db;
    this.access = access;
    this.audit = audit;
    this.inventory = inventory;
  }

  @GetMapping("/plans")
  ApiResponse<?> plans(@RequestParam long locationId) {
    access.require("inventory:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT p.*,s.name supplierName FROM pur_plan p JOIN md_supplier s ON"
                + " s.id=p.supplier_id WHERE location_id=? ORDER BY created_at DESC",
            locationId));
  }

  @GetMapping("/plans/{id}")
  ApiResponse<?> detail(@PathVariable long id) {
    access.require("inventory:read");
    var p = db.queryForMap("SELECT * FROM pur_plan WHERE id=?", id);
    access.location(id(p.get("location_id")));
    return ApiResponse.ok(
        Map.of(
            "header",
            p,
            "items",
            db.queryForList(
                "SELECT x.*,i.name,i.specification,u.name unit FROM pur_plan_item x JOIN md_item i"
                    + " ON i.id=x.item_id JOIN md_unit u ON u.id=i.base_unit_id WHERE plan_id=?",
                id)));
  }

  @PostMapping("/plans")
  @Transactional
  ApiResponse<?> create(@RequestBody Map<String, Object> r) {
    access.require("inventory:write");
    long location = id(r.get("locationId"));
    access.location(location);
    if (!(r.get("items") instanceof List<?> items) || items.isEmpty())
      throw new BusinessException("VALIDATION_FAILED", "请选择采购品种");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO pur_plan(id,number,location_id,supplier_id,created_by) VALUES (?,?,?,?,?)",
        id,
        "PO" + id,
        location,
        id(r.get("supplierId")),
        access.current().id());
    for (Object value : items) {
      var line = (Map<?, ?>) value;
      var qty = decimal(line.get("quantity"));
      positive(qty, "采购数量");
      db.update(
          "INSERT INTO pur_plan_item VALUES (?,?,?,?)",
          IdWorker.getId(),
          id,
          id(line.get("itemId")),
          qty);
    }
    audit.record("CREATE", "PURCHASE_PLAN", id, null);
    return ApiResponse.ok(Map.of("id", id));
  }

  @PostMapping("/plans/{id}/approve")
  @Transactional
  ApiResponse<?> approve(@PathVariable long id) {
    access.require("inventory:approve");
    var p = db.queryForMap("SELECT * FROM pur_plan WHERE id=? FOR UPDATE", id);
    access.location(id(p.get("location_id")));
    InventoryService.requireState(text(p, "status"), "SUBMITTED");
    if (id(p.get("created_by")) == access.current().id())
      throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "采购制单人与审核人须不同");
    db.update(
        "UPDATE pur_plan SET status='APPROVED',approved_by=? WHERE id=?",
        access.current().id(),
        id);
    audit.record("APPROVE", "PURCHASE_PLAN", id, null);
    return ApiResponse.ok(null);
  }

  @PostMapping("/plans/{id}/link-receipt")
  @Transactional
  ApiResponse<?> link(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("inventory:write");
    var p = db.queryForMap("SELECT * FROM pur_plan WHERE id=? FOR UPDATE", id);
    access.location(id(p.get("location_id")));
    InventoryService.requireState(text(p, "status"), "APPROVED");
    var d = db.queryForMap("SELECT * FROM inv_document WHERE id=?", id(r.get("documentId")));
    if (!text(d, "type").equals("RECEIPT")
        || id(d.get("location_id")) != id(p.get("location_id"))
        || id(d.get("supplier_id")) != id(p.get("supplier_id")))
      throw new BusinessException("VALIDATION_FAILED", "入库单机构或供应商不匹配");
    db.update(
        "UPDATE pur_plan SET receipt_document_id=?,status='ORDERED' WHERE id=?", d.get("id"), id);
    audit.record("LINK_RECEIPT", "PURCHASE_PLAN", id, null);
    return ApiResponse.ok(null);
  }

  @PostMapping("/plans/{id}/receipt")
  @Transactional
  ApiResponse<?> receipt(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("inventory:write");
    var p = db.queryForMap("SELECT * FROM pur_plan WHERE id=? FOR UPDATE", id);
    access.location(id(p.get("location_id")));
    InventoryService.requireState(text(p, "status"), "APPROVED");
    String batch = text(r, "batchNo"),
        made = text(r, "manufacturedOn"),
        expiry = text(r, "expiresOn");
    var items = new ArrayList<Map<String, Object>>();
    for (var line :
        db.queryForList(
            "SELECT x.*,i.base_unit_id FROM pur_plan_item x JOIN md_item i ON i.id=x.item_id WHERE"
                + " x.plan_id=?",
            id))
      items.add(
          Map.of(
              "itemId",
              line.get("item_id"),
              "unitId",
              line.get("base_unit_id"),
              "quantity",
              line.get("base_qty"),
              "batchNo",
              batch + "-" + line.get("item_id"),
              "manufacturedOn",
              made,
              "expiresOn",
              expiry,
              "unitCost",
              r.get("unitCost"),
              "manufacturerId",
              1));
    long doc =
        inventory.create(
            Map.of(
                "type",
                "RECEIPT",
                "locationId",
                p.get("location_id"),
                "supplierId",
                p.get("supplier_id"),
                "reason",
                "采购计划 " + p.get("number"),
                "items",
                items));
    db.update("UPDATE pur_plan SET receipt_document_id=?,status='ORDERED' WHERE id=?", doc, id);
    return ApiResponse.ok(Map.of("documentId", doc));
  }
}
