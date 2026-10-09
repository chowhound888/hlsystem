package com.hlsystem.inventory;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryCorrectionController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final InventoryService inventory;
  private final AccountingService accounting;
  private final AuditService audit;

  public InventoryCorrectionController(
      JdbcTemplate db,
      AccessService access,
      InventoryService inventory,
      AccountingService accounting,
      AuditService audit) {
    this.db = db;
    this.access = access;
    this.inventory = inventory;
    this.accounting = accounting;
    this.audit = audit;
  }

  @PostMapping("/documents/{id}/reversals")
  @Transactional
  ApiResponse<?> reverse(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("inventory:approve");
    accounting.openPeriod();
    var original = inventory.doc(id, true);
    access.location(id(original.get("location_id")));
    InventoryService.requireState(text(original, "status"), "EXECUTED");
    if (!Set.of("RECEIPT", "ISSUE", "LOSS").contains(text(original, "type")))
      throw new BusinessException("INVALID_STATE", "此类型请使用退药、退供或在途处理纠错");
    if (id(original.get("executed_by")) == access.current().id())
      throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "冲销确认人与原执行人须不同");
    String reason = text(r, "reason");
    if (reason.isBlank()) throw new BusinessException("VALIDATION_FAILED", "请填写冲销原因");
    inventory.checkStocktake(id(original.get("location_id")));
    long doc = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason,reversal_of_id)"
            + " VALUES (?,?,'REVERSAL',?,'EXECUTED',?,?,?,?)",
        doc,
        "REV" + doc,
        original.get("location_id"),
        LocalDate.now(),
        access.current().id(),
        reason,
        id);
    BigDecimal total = BigDecimal.ZERO;
    for (var x :
        db.queryForList("SELECT * FROM inv_document_item WHERE document_id=? ORDER BY id", id)) {
      long line = IdWorker.getId();
      db.update(
          "INSERT INTO"
              + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty,cost_amount,original_item_id)"
              + " VALUES (?,?,?,?,?,?,?,?,?)",
          line,
          doc,
          x.get("item_id"),
          x.get("unit_id"),
          x.get("quantity"),
          x.get("factor_snapshot"),
          x.get("base_qty"),
          x.get("cost_amount"),
          x.get("id"));
      for (var leg :
          db.queryForList(
              "SELECT * FROM inv_ledger WHERE document_item_id=? ORDER BY stock_id", x.get("id"))) {
        var stock =
            db.queryForMap("SELECT * FROM inv_stock WHERE id=? FOR UPDATE", leg.get("stock_id"));
        BigDecimal qty = decimal(leg.get("qty_delta")).negate(),
            cost = decimal(leg.get("cost_delta")).negate();
        if (qty.signum() < 0
            && decimal(stock.get("on_hand_qty"))
                    .subtract(decimal(stock.get("reserved_qty")))
                    .compareTo(qty.negate())
                < 0) throw new BusinessException("STOCK_INSUFFICIENT", "原入库数量已消耗或预留，不能整单冲销");
        inventory.delta(stock, qty, cost, line, "REVERSE:" + leg.get("id"));
      }
      total = total.add(decimal(x.get("cost_amount")));
    }
    if (text(original, "type").equals("RECEIPT")) {
      var payables =
          db.queryForList("SELECT * FROM acc_payable WHERE document_id=? FOR UPDATE", id);
      if (!payables.isEmpty()) {
        if (decimal(payables.getFirst().get("paid_amount")).signum() > 0)
          throw new BusinessException("PAYMENT_ALREADY_MADE", "已付款采购请使用退供和供应商退款");
        db.update("UPDATE acc_payable SET amount=0 WHERE document_id=?", id);
      }
      accounting.post(
          "REV:" + id, "REVERSAL", doc, "入库冲销", total, 2, 1, id(original.get("location_id")));
    } else
      accounting.post(
          "REV:" + id,
          "REVERSAL",
          doc,
          "出库冲销",
          total,
          1,
          text(original, "type").equals("LOSS") ? 4 : 3,
          id(original.get("location_id")));
    db.update("UPDATE inv_document SET status='REVERSED',version=version+1 WHERE id=?", id);
    audit.record("REVERSE", "INVENTORY", id, reason);
    return ApiResponse.ok(Map.of("documentId", doc));
  }

  @GetMapping("/transit-adjustments")
  ApiResponse<?> adjustments(@RequestParam long locationId) {
    access.require("inventory:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT t.*,i.name,d.number transferNumber FROM inv_transit_adjustment t JOIN"
                + " inv_allocation a ON a.id=t.allocation_id JOIN inv_document_item x ON"
                + " x.id=a.document_item_id JOIN inv_document d ON d.id=x.document_id JOIN md_item"
                + " i ON i.id=x.item_id WHERE d.location_id=? OR d.target_location_id=? ORDER BY"
                + " t.created_at DESC",
            locationId,
            locationId));
  }

  @PostMapping("/transit-adjustments")
  @Transactional
  ApiResponse<?> requestAdjustment(@RequestBody Map<String, Object> r) {
    access.require("inventory:write");
    var allocation =
        db.queryForMap(
            "SELECT a.*,d.location_id,d.target_location_id FROM inv_allocation a JOIN"
                + " inv_document_item x ON x.id=a.document_item_id JOIN inv_document d ON"
                + " d.id=x.document_id WHERE a.id=?",
            id(r.get("allocationId")));
    if (!access.current().locationIds().contains(id(allocation.get("location_id"))))
      access.location(id(allocation.get("target_location_id")));
    String type = text(r, "type"), reason = text(r, "reason");
    if (!Set.of("LOSS", "RETURN").contains(type) || reason.isBlank())
      throw new BusinessException("VALIDATION_FAILED", "请填写在途处理类型和原因");
    BigDecimal qty = decimal(r.get("quantity"));
    positive(qty, "在途处理数量");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO inv_transit_adjustment(id,allocation_id,type,quantity,reason,created_by)"
            + " VALUES (?,?,?,?,?,?)",
        id,
        allocation.get("id"),
        type,
        qty,
        reason,
        access.current().id());
    audit.record("CREATE", "TRANSIT_ADJUSTMENT", id, reason);
    return ApiResponse.ok(Map.of("id", id));
  }

  @PostMapping("/transit-adjustments/{id}/execute")
  @Transactional
  ApiResponse<?> executeAdjustment(@PathVariable long id) {
    access.require("inventory:approve");
    accounting.openPeriod();
    var t = db.queryForMap("SELECT * FROM inv_transit_adjustment WHERE id=? FOR UPDATE", id);
    InventoryService.requireState(text(t, "status"), "SUBMITTED");
    if (id(t.get("created_by")) == access.current().id())
      throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "申请人与确认人须不同");
    var a =
        db.queryForMap(
            "SELECT a.*,x.item_id,x.unit_id,x.document_id,d.location_id FROM inv_allocation a JOIN"
                + " inv_document_item x ON x.id=a.document_item_id JOIN inv_document d ON"
                + " d.id=x.document_id WHERE a.id=? FOR UPDATE",
            t.get("allocation_id"));
    long location = id(a.get("location_id"));
    access.location(location);
    BigDecimal
        remaining =
            decimal(a.get("base_qty"))
                .subtract(decimal(a.get("received_qty")))
                .subtract(decimal(a.get("returned_qty")))
                .subtract(decimal(a.get("lost_qty"))),
        qty = decimal(t.get("quantity"));
    if (qty.compareTo(remaining) > 0)
      throw new BusinessException("QUANTITY_EXCEEDED", "处理数量超过剩余在途");
    BigDecimal
        remainingCost =
            decimal(a.get("cost_amount"))
                .subtract(decimal(a.get("received_cost")))
                .subtract(decimal(a.get("returned_cost")))
                .subtract(decimal(a.get("lost_cost"))),
        cost =
            qty.compareTo(remaining) == 0
                ? remainingCost
                : money(remainingCost.multiply(qty).divide(remaining, 8, RoundingMode.HALF_UP));
    long doc = IdWorker.getId(), line = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'TRANSIT_ADJUSTMENT',?,'EXECUTED',?,?,?)",
        doc,
        "TA" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        t.get("reason"));
    db.update(
        "INSERT INTO"
            + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty,cost_amount)"
            + " VALUES (?,?,?,?,?,1,?,?)",
        line,
        doc,
        a.get("item_id"),
        a.get("unit_id"),
        qty,
        qty,
        cost);
    var transit =
        db.queryForMap(
            "SELECT * FROM inv_stock WHERE location_id=9 AND cost_layer_id=? AND"
                + " stock_status='AVAILABLE' FOR UPDATE",
            a.get("cost_layer_id"));
    inventory.delta(transit, qty.negate(), cost.negate(), line, "TRANSIT-ADJUST:" + id);
    if (text(t, "type").equals("RETURN")) {
      inventory.add(
          location,
          id(a.get("cost_layer_id")),
          "AVAILABLE",
          qty,
          cost,
          line,
          "TRANSIT-RETURN:" + id);
      db.update(
          "UPDATE inv_allocation SET returned_qty=returned_qty+?,returned_cost=returned_cost+?"
              + " WHERE id=?",
          qty,
          cost,
          a.get("id"));
    } else {
      db.update(
          "UPDATE inv_allocation SET lost_qty=lost_qty+?,lost_cost=lost_cost+? WHERE id=?",
          qty,
          cost,
          a.get("id"));
      accounting.post("TRANSIT-LOSS:" + id, "INVENTORY", doc, "在途短少报损", cost, 4, 1, location);
    }
    db.update(
        "UPDATE inv_transit_adjustment SET status='EXECUTED',approved_by=?,document_id=? WHERE"
            + " id=?",
        access.current().id(),
        doc,
        id);
    BigDecimal balance =
        db.queryForObject(
            "SELECT SUM(a.base_qty-a.received_qty-a.returned_qty-a.lost_qty) FROM inv_allocation a"
                + " JOIN inv_document_item x ON x.id=a.document_item_id WHERE x.document_id=?",
            BigDecimal.class,
            a.get("document_id"));
    if (balance.signum() == 0)
      db.update(
          "UPDATE inv_document SET status='RECEIVED',version=version+1 WHERE id=?",
          a.get("document_id"));
    audit.record("EXECUTE", "TRANSIT_ADJUSTMENT", id, text(t, "reason"));
    return ApiResponse.ok(null);
  }
}
