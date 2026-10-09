package com.hlsystem.preparation;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.common.*;
import com.hlsystem.inventory.InventoryService;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/preparation/orders")
public class MaterialReturnController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final InventoryService inventory;
  private final AccountingService accounting;
  private final AuditService audit;

  public MaterialReturnController(
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

  @GetMapping("/{id}/materials")
  ApiResponse<?> materials(@PathVariable long id) {
    access.require("preparation:read");
    var order = db.queryForMap("SELECT * FROM prep_production_order WHERE id=?", id);
    access.location(id(order.get("location_id")));
    return ApiResponse.ok(
        db.queryForList(
            "SELECT a.*,i.name,u.name unit,b.batch_no,a.base_qty-a.returned_qty remaining FROM"
                + " inv_allocation a JOIN inv_document_item x ON x.id=a.document_item_id JOIN"
                + " md_item i ON i.id=x.item_id JOIN md_unit u ON u.id=i.base_unit_id JOIN"
                + " inv_cost_layer c ON c.id=a.cost_layer_id JOIN inv_batch b ON b.id=c.batch_id"
                + " WHERE x.document_id=? ORDER BY a.id",
            order.get("issue_document_id")));
  }

  @PostMapping("/{id}/material-returns")
  @Transactional
  ApiResponse<?> returnMaterial(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("preparation:write");
    accounting.openPeriod();
    var order = db.queryForMap("SELECT * FROM prep_production_order WHERE id=? FOR UPDATE", id);
    InventoryService.requireState(text(order, "status"), "PRODUCING");
    long location = id(order.get("location_id"));
    access.location(location);
    var a =
        db.queryForMap(
            "SELECT a.*,x.item_id,x.unit_id FROM inv_allocation a JOIN inv_document_item x ON"
                + " x.id=a.document_item_id WHERE a.id=? AND x.document_id=? FOR UPDATE",
            id(r.get("allocationId")),
            order.get("issue_document_id"));
    BigDecimal qty = decimal(r.get("quantity")),
        remaining = decimal(a.get("base_qty")).subtract(decimal(a.get("returned_qty")));
    positive(qty, "退料基础数量");
    if (qty.compareTo(remaining) > 0 || text(r, "reason").isBlank())
      throw new BusinessException("VALIDATION_FAILED", "退料超量或缺少原因");
    BigDecimal cost =
        qty.compareTo(remaining) == 0
            ? decimal(a.get("cost_amount")).subtract(decimal(a.get("returned_cost")))
            : money(
                decimal(a.get("cost_amount"))
                    .multiply(qty)
                    .divide(decimal(a.get("base_qty")), 8, RoundingMode.HALF_UP));
    long doc = IdWorker.getId(), line = IdWorker.getId(), returnId = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'MATERIAL_RETURN',?,'EXECUTED',?,?,?)",
        doc,
        "MR" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        text(r, "reason"));
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
    inventory.add(
        location,
        id(a.get("cost_layer_id")),
        "AVAILABLE",
        qty,
        cost,
        line,
        "MATERIAL-RETURN:" + returnId);
    db.update(
        "UPDATE inv_allocation SET returned_qty=returned_qty+?,returned_cost=returned_cost+? WHERE"
            + " id=?",
        qty,
        cost,
        a.get("id"));
    db.update(
        "UPDATE prep_production_order SET material_cost=material_cost-? WHERE id=?", cost, id);
    db.update(
        "INSERT INTO"
            + " prep_material_return(id,production_order_id,allocation_id,document_id,base_qty,cost_amount,reason,created_by)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        returnId,
        id,
        a.get("id"),
        doc,
        qty,
        cost,
        text(r, "reason"),
        access.current().id());
    accounting.post(
        "PREP-RETURN:" + returnId, "PRODUCTION", id, "生产退料冲回在制成本", cost, 1, 9, location);
    audit.record("MATERIAL_RETURN", "PRODUCTION", id, text(r, "reason"));
    return ApiResponse.ok(null);
  }
}
