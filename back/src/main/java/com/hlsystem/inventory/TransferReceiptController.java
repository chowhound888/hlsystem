package com.hlsystem.inventory;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/transfers")
public class TransferReceiptController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final InventoryService inventory;
  private final AccountingService accounting;
  private final AuditService audit;

  public TransferReceiptController(
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

  @GetMapping("/{id}/allocations")
  ApiResponse<?> allocations(@PathVariable long id) {
    access.require("inventory:read");
    var d = inventory.doc(id, false);
    if (!access.current().locationIds().contains(id(d.get("target_location_id"))))
      access.location(id(d.get("location_id")));
    return ApiResponse.ok(
        db.queryForList(
            "SELECT a.*,i.name,b.batch_no,a.base_qty-a.received_qty-a.returned_qty-a.lost_qty"
                + " remaining_qty FROM inv_allocation a JOIN inv_document_item x ON"
                + " x.id=a.document_item_id JOIN md_item i ON i.id=x.item_id JOIN inv_cost_layer c"
                + " ON c.id=a.cost_layer_id JOIN inv_batch b ON b.id=c.batch_id WHERE"
                + " x.document_id=? ORDER BY a.id",
            id));
  }

  @PostMapping("/{id}/receipts")
  @Transactional
  ApiResponse<?> receive(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("inventory:execute");
    accounting.openPeriod();
    var d = inventory.doc(id, true);
    InventoryService.requireState(text(d, "status"), "SHIPPED", "PARTIALLY_RECEIVED");
    long target = id(d.get("target_location_id"));
    access.location(target);
    inventory.checkStocktake(target);
    if (!(r.get("items") instanceof List<?> items) || items.isEmpty())
      throw new BusinessException("VALIDATION_FAILED", "请选择接收批次数量");
    long receipt = IdWorker.getId();
    db.update(
        "INSERT INTO inv_transfer_receipt(id,document_id,operator_id) VALUES (?,?,?)",
        receipt,
        id,
        access.current().id());
    for (Object value : items) {
      var line = (Map<?, ?>) value;
      var a =
          db.queryForMap(
              "SELECT a.* FROM inv_allocation a JOIN inv_document_item x ON x.id=a.document_item_id"
                  + " WHERE a.id=? AND x.document_id=? FOR UPDATE",
              id(line.get("allocationId")),
              id);
      BigDecimal qty = decimal(line.get("quantity")),
          remaining =
              decimal(a.get("base_qty"))
                  .subtract(decimal(a.get("received_qty")))
                  .subtract(decimal(a.get("returned_qty")))
                  .subtract(decimal(a.get("lost_qty")));
      positive(qty, "接收数量");
      if (qty.compareTo(remaining) > 0)
        throw new BusinessException("RECEIPT_LIMIT_EXCEEDED", "接收数量超出在途余额");
      BigDecimal cost =
          qty.compareTo(remaining) == 0
              ? decimal(a.get("cost_amount"))
                  .subtract(decimal(a.get("received_cost")))
                  .subtract(decimal(a.get("returned_cost")))
                  .subtract(decimal(a.get("lost_cost")))
              : money(
                  decimal(a.get("cost_amount"))
                      .multiply(qty)
                      .divide(decimal(a.get("base_qty")), 8, RoundingMode.HALF_UP));
      var transit =
          db.queryForMap(
              "SELECT * FROM inv_stock WHERE location_id=9 AND cost_layer_id=? AND"
                  + " stock_status='AVAILABLE' FOR UPDATE",
              a.get("cost_layer_id"));
      long receiptLine = IdWorker.getId();
      inventory.delta(
          transit,
          qty.negate(),
          cost.negate(),
          id(a.get("document_item_id")),
          "PARTIAL-OUT:" + receiptLine);
      inventory.add(
          target,
          id(a.get("cost_layer_id")),
          "AVAILABLE",
          qty,
          cost,
          id(a.get("document_item_id")),
          "PARTIAL-IN:" + receiptLine);
      db.update(
          "UPDATE inv_allocation SET received_qty=received_qty+?,received_cost=received_cost+?"
              + " WHERE id=?",
          qty,
          cost,
          a.get("id"));
      db.update(
          "INSERT INTO inv_transfer_receipt_item VALUES (?,?,?,?,?)",
          receiptLine,
          receipt,
          a.get("id"),
          qty,
          cost);
    }
    BigDecimal balance =
        db.queryForObject(
            "SELECT COALESCE(SUM(a.base_qty-a.received_qty-a.returned_qty-a.lost_qty),0) FROM"
                + " inv_allocation a JOIN inv_document_item x ON x.id=a.document_item_id WHERE"
                + " x.document_id=?",
            BigDecimal.class,
            id);
    String state = balance.signum() == 0 ? "RECEIVED" : "PARTIALLY_RECEIVED";
    db.update("UPDATE inv_document SET status=?,version=version+1 WHERE id=?", state, id);
    audit.transition("INVENTORY", id, text(d, "status"), state, "PARTIAL_RECEIVE", null);
    return ApiResponse.ok(Map.of("remaining", balance));
  }
}
