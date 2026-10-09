package com.hlsystem.inpatient;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.common.*;
import com.hlsystem.inventory.InventoryService;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inpatient")
public class InpatientReturnController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final AccountingService accounting;
  private final InventoryService inventory;
  private final AuditService audit;

  public InpatientReturnController(
      JdbcTemplate db,
      AccessService access,
      AccountingService accounting,
      InventoryService inventory,
      AuditService audit) {
    this.db = db;
    this.access = access;
    this.accounting = accounting;
    this.inventory = inventory;
    this.audit = audit;
  }

  @PostMapping("/executions/{id}/returns")
  @Transactional
  ApiResponse<?> returnDrugs(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("inpatient:write");
    accounting.openPeriod();
    var ex = db.queryForMap("SELECT * FROM ip_order_execution WHERE id=? FOR UPDATE", id);
    InventoryService.requireState(text(ex, "status"), "DISPENSED");
    var o = db.queryForMap("SELECT * FROM ip_medical_order WHERE id=?", ex.get("order_id"));
    long location = id(o.get("location_id"));
    access.location(location);
    String reason = text(r, "reason");
    if (reason.isBlank()) throw new BusinessException("VALIDATION_FAILED", "请填写退药原因");
    var original =
        db.queryForMap(
            "SELECT * FROM inv_document_item WHERE document_id=?", ex.get("inventory_document_id"));
    long doc = IdWorker.getId(), line = IdWorker.getId(), returnId = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'RETURN',?,'EXECUTED',?,?,?)",
        doc,
        "IPR" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        reason);
    db.update(
        "INSERT INTO"
            + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty)"
            + " VALUES (?,?,?,?,?,1,?)",
        line,
        doc,
        original.get("item_id"),
        original.get("unit_id"),
        original.get("base_qty"),
        original.get("base_qty"));
    BigDecimal total = BigDecimal.ZERO;
    for (var a :
        db.queryForList(
            "SELECT * FROM inv_allocation WHERE document_item_id=? ORDER BY id FOR UPDATE",
            original.get("id"))) {
      BigDecimal qty = decimal(a.get("base_qty")), cost = decimal(a.get("cost_amount"));
      inventory.add(
          location,
          id(a.get("cost_layer_id")),
          "QUARANTINE",
          qty,
          cost,
          line,
          "IP-RETURN:" + returnId + ":" + a.get("id"));
      db.update(
          "UPDATE inv_allocation SET returned_qty=base_qty,returned_cost=cost_amount WHERE id=?",
          a.get("id"));
      total = total.add(cost);
    }
    db.update("UPDATE inv_document_item SET cost_amount=? WHERE id=?", total, line);
    var account =
        db.queryForMap(
            "SELECT * FROM cl_deposit_account WHERE encounter_id=? FOR UPDATE",
            o.get("encounter_id"));
    BigDecimal amount = decimal(ex.get("amount")),
        balance = decimal(account.get("balance")).add(amount);
    db.update(
        "UPDATE cl_deposit_account SET balance=?,charged_amount=charged_amount-? WHERE id=?",
        balance,
        amount,
        account.get("id"));
    db.update(
        "INSERT INTO"
            + " cl_deposit_ledger(id,account_id,event_key,delta,balance_after,reason,operator_id)"
            + " VALUES (?,?,?,?,?,?,?)",
        IdWorker.getId(),
        account.get("id"),
        "IP-RETURN:" + returnId,
        amount,
        balance,
        reason,
        access.current().id());
    db.update("UPDATE ip_order_execution SET status='RETURNED' WHERE id=?", id);
    db.update(
        "UPDATE cl_charge SET status='RETURNED',refunded_amount=amount WHERE"
            + " source_type='EXECUTION' AND source_id=?",
        id);
    db.update(
        "INSERT INTO ip_return_order(id,execution_id,document_id,amount,reason,created_by) VALUES"
            + " (?,?,?,?,?,?)",
        returnId,
        id,
        doc,
        amount,
        reason,
        access.current().id());
    accounting.post(
        "IP-RETURN-COST:" + returnId, "RETURN", returnId, "住院退药成本", total, 1, 3, location);
    accounting.post(
        "IP-RETURN-INCOME:" + returnId, "RETURN", returnId, "住院退药冲回收入", amount, 6, 7, location);
    audit.record("RETURN", "EXECUTION", id, reason);
    return ApiResponse.ok(null);
  }

  @GetMapping("/ward-quotas")
  ApiResponse<?> quotas(@RequestParam long locationId) {
    access.require("inpatient:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT q.*,i.name,i.specification FROM ip_ward_quota q JOIN md_item i ON"
                + " i.id=q.item_id WHERE q.location_id=?",
            locationId));
  }

  @PostMapping("/ward-quotas")
  @Transactional
  ApiResponse<?> quota(@RequestBody Map<String, Object> r) {
    access.require("inpatient:write");
    long location = id(r.get("locationId"));
    access.location(location);
    if (!"WARD"
        .equals(
            db.queryForObject("SELECT type FROM sys_location WHERE id=?", String.class, location)))
      throw new BusinessException("VALIDATION_FAILED", "请选择病区库存机构");
    BigDecimal qty = decimal(r.get("quantity"));
    positive(qty, "基数数量");
    db.update(
        "INSERT INTO ip_ward_quota VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " quota_qty=VALUES(quota_qty)",
        IdWorker.getId(),
        location,
        id(r.get("itemId")),
        qty);
    return ApiResponse.ok(null);
  }

  @GetMapping("/ward-usage")
  ApiResponse<?> wardUsage(@RequestParam long locationId) {
    access.require("inpatient:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT w.*,i.name,p.name patientName FROM ip_ward_usage w JOIN md_item i ON"
                + " i.id=w.item_id LEFT JOIN cl_encounter e ON e.id=w.encounter_id LEFT JOIN"
                + " cl_patient p ON p.id=e.patient_id WHERE w.location_id=? ORDER BY w.used_at"
                + " DESC",
            locationId));
  }

  @PostMapping("/ward-usage")
  @Transactional
  ApiResponse<?> useWard(@RequestBody Map<String, Object> r) {
    access.require("inpatient:write");
    accounting.openPeriod();
    long location = id(r.get("locationId")), item = id(r.get("itemId"));
    access.location(location);
    if (!"WARD"
        .equals(
            db.queryForObject("SELECT type FROM sys_location WHERE id=?", String.class, location)))
      throw new BusinessException("VALIDATION_FAILED", "请选择病区机构");
    String purpose = text(r, "purpose");
    if (purpose.isBlank()) throw new BusinessException("VALIDATION_FAILED", "请填写用药用途");
    BigDecimal qty = decimal(r.get("quantity"));
    positive(qty, "消耗数量");
    long doc = IdWorker.getId(), line = IdWorker.getId(), usage = IdWorker.getId();
    Long unit = db.queryForObject("SELECT base_unit_id FROM md_item WHERE id=?", Long.class, item);
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'WARD_USAGE',?,'EXECUTED',?,?,?)",
        doc,
        "WU" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        purpose);
    db.update(
        "INSERT INTO"
            + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty)"
            + " VALUES (?,?,?,?,?,1,?)",
        line,
        doc,
        item,
        unit,
        qty,
        qty);
    var allocations = inventory.remove(location, item, qty, line, "WARD_USAGE");
    BigDecimal cost =
        allocations.stream()
            .map(a -> decimal(a.get("cost_amount")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    db.update("UPDATE inv_document_item SET cost_amount=? WHERE id=?", cost, line);
    db.update(
        "INSERT INTO"
            + " ip_ward_usage(id,location_id,item_id,document_id,base_qty,amount,purpose,operator_id)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        usage,
        location,
        item,
        doc,
        qty,
        cost,
        purpose,
        access.current().id());
    accounting.post("WARD-COST:" + usage, "WARD_USAGE", usage, "病区基数药消耗", cost, 3, 1, location);
    audit.record("USAGE", "WARD", usage, purpose);
    return ApiResponse.ok(null);
  }
}
