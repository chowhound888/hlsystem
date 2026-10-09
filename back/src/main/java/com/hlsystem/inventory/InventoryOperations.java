package com.hlsystem.inventory;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryOperations {
  private final JdbcTemplate db;
  private final AccessService access;
  private final InventoryService inventory;
  private final AccountingService accounting;
  private final AuditService audit;
  private final ObjectMapper json;

  public InventoryOperations(
      JdbcTemplate db,
      AccessService access,
      InventoryService inventory,
      AccountingService accounting,
      AuditService audit,
      ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.inventory = inventory;
    this.accounting = accounting;
    this.audit = audit;
    this.json = json;
  }

  @GetMapping("/suggestions")
  ApiResponse<?> suggestions(@RequestParam long locationId) {
    access.require("inventory:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT i.id itemId,i.name,i.specification,u.name"
                + " unit,m.min_qty,m.target_qty,COALESCE(st.available,0)"
                + " available,GREATEST(0,m.target_qty-COALESCE(st.available,0)) suggested FROM"
                + " md_location_item m JOIN md_item i ON i.id=m.item_id JOIN md_unit u ON"
                + " u.id=i.base_unit_id LEFT JOIN (SELECT"
                + " b.item_id,SUM(s.on_hand_qty-s.reserved_qty) available FROM inv_stock s JOIN"
                + " inv_cost_layer c ON c.id=s.cost_layer_id JOIN inv_batch b ON b.id=c.batch_id"
                + " WHERE s.location_id=? AND s.stock_status='AVAILABLE' AND"
                + " b.quality_status='PASSED' AND b.expires_on>=CURDATE() GROUP BY b.item_id) st ON"
                + " st.item_id=i.id WHERE m.location_id=? AND i.enabled=TRUE",
            locationId,
            locationId));
  }

  @GetMapping("/stocktakes")
  ApiResponse<?> stocktakes(@RequestParam long locationId) {
    access.require("inventory:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT * FROM inv_stocktake WHERE location_id=? ORDER BY created_at DESC",
            locationId));
  }

  @PostMapping("/stocktakes")
  @Transactional
  ApiResponse<?> start(@RequestBody Map<String, Object> r) {
    access.require("inventory:write");
    long location = id(r.get("locationId"));
    access.location(location);
    accounting.openPeriod();
    db.queryForMap("SELECT * FROM sys_location WHERE id=? FOR UPDATE", location);
    inventory.checkStocktake(location);
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO inv_stocktake(id,number,location_id,created_by) VALUES (?,?,?,?)",
        id,
        "ST" + id,
        location,
        access.current().id());
    for (var stock :
        db.queryForList(
            "SELECT * FROM inv_stock WHERE location_id=? AND on_hand_qty>0 ORDER BY id FOR UPDATE",
            location))
      db.update(
          "INSERT INTO inv_stocktake_item(id,stocktake_id,stock_id,snapshot_qty,counted_qty) VALUES"
              + " (?,?,?,?,?)",
          IdWorker.getId(),
          id,
          stock.get("id"),
          stock.get("on_hand_qty"),
          stock.get("on_hand_qty"));
    audit.record("START", "STOCKTAKE", id, null);
    return ApiResponse.ok(Map.of("id", id));
  }

  @GetMapping("/stocktakes/{id}")
  ApiResponse<?> stocktake(@PathVariable long id) {
    access.require("inventory:read");
    var t = db.queryForMap("SELECT * FROM inv_stocktake WHERE id=?", id);
    access.location(id(t.get("location_id")));
    return ApiResponse.ok(
        Map.of(
            "header",
            t,
            "items",
            db.queryForList(
                "SELECT x.*,i.name,b.batch_no,u.name unit FROM inv_stocktake_item x JOIN inv_stock"
                    + " s ON s.id=x.stock_id JOIN inv_cost_layer c ON c.id=s.cost_layer_id JOIN"
                    + " inv_batch b ON b.id=c.batch_id JOIN md_item i ON i.id=b.item_id JOIN"
                    + " md_unit u ON u.id=i.base_unit_id WHERE x.stocktake_id=? ORDER BY"
                    + " i.code,b.batch_no",
                id)));
  }

  @PostMapping("/stocktakes/{id}/submit")
  @Transactional
  ApiResponse<?> submitCount(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("inventory:write");
    var t = db.queryForMap("SELECT * FROM inv_stocktake WHERE id=? FOR UPDATE", id);
    access.location(id(t.get("location_id")));
    InventoryService.requireState(text(t, "status"), "COUNTING");
    if (!(r.get("items") instanceof List<?> items))
      throw new BusinessException("VALIDATION_FAILED", "请填写实盘数量");
    for (Object value : items) {
      var x = (Map<?, ?>) value;
      BigDecimal qty = decimal(x.get("countedQty"));
      if (qty.signum() < 0) throw new BusinessException("VALIDATION_FAILED", "实盘数不能为负");
      if (db.update(
              "UPDATE inv_stocktake_item SET counted_qty=?,reason=? WHERE id=? AND stocktake_id=?",
              qty,
              x.get("reason"),
              id(x.get("id")),
              id)
          != 1) throw new BusinessException("VALIDATION_FAILED", "盘点明细无效");
    }
    db.update("UPDATE inv_stocktake SET status='SUBMITTED' WHERE id=?", id);
    audit.record("SUBMIT", "STOCKTAKE", id, null);
    return ApiResponse.ok(null);
  }

  @PostMapping("/stocktakes/{id}/execute")
  @Transactional
  ApiResponse<?> executeCount(@PathVariable long id) {
    access.require("inventory:approve");
    accounting.openPeriod();
    var t = db.queryForMap("SELECT * FROM inv_stocktake WHERE id=? FOR UPDATE", id);
    long location = id(t.get("location_id"));
    access.location(location);
    InventoryService.requireState(text(t, "status"), "SUBMITTED");
    if (id(t.get("created_by")) == access.current().id())
      throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "盘点人与审核人须不同");
    long doc = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'STOCKTAKE',?,'EXECUTED',?,?,?)",
        doc,
        "STD" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        "盘点差异");
    for (var x :
        db.queryForList(
            "SELECT x.*,c.base_unit_cost,b.item_id,i.base_unit_id FROM inv_stocktake_item x JOIN"
                + " inv_stock s ON s.id=x.stock_id JOIN inv_cost_layer c ON c.id=s.cost_layer_id"
                + " JOIN inv_batch b ON b.id=c.batch_id JOIN md_item i ON i.id=b.item_id WHERE"
                + " x.stocktake_id=? ORDER BY x.stock_id",
            id)) {
      BigDecimal delta = decimal(x.get("counted_qty")).subtract(decimal(x.get("snapshot_qty")));
      if (delta.signum() == 0) continue;
      if (text(x, "reason").isBlank()) throw new BusinessException("VALIDATION_FAILED", "差异必须填写原因");
      var s = db.queryForMap("SELECT * FROM inv_stock WHERE id=? FOR UPDATE", x.get("stock_id"));
      if (decimal(s.get("reserved_qty")).compareTo(decimal(x.get("counted_qty"))) > 0)
        throw new BusinessException("STOCK_RESERVED", "盘点数少于预留数，请先解除预留");
      BigDecimal cost =
          delta.signum() < 0 && delta.abs().compareTo(decimal(s.get("on_hand_qty"))) == 0
              ? decimal(s.get("remaining_cost")).negate()
              : money(delta.multiply(decimal(x.get("base_unit_cost"))));
      long line = IdWorker.getId();
      db.update(
          "INSERT INTO"
              + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty,cost_amount)"
              + " VALUES (?,?,?,?,?,1,?,?)",
          line,
          doc,
          x.get("item_id"),
          x.get("base_unit_id"),
          delta.abs(),
          delta.abs(),
          cost.abs());
      inventory.delta(s, delta, cost, line, "COUNT:" + x.get("id"));
      accounting.post(
          "COUNT:" + x.get("id"),
          "STOCKTAKE",
          id,
          "盘点差异",
          cost.abs(),
          delta.signum() > 0 ? 1 : 4,
          delta.signum() > 0 ? 4 : 1,
          location);
    }
    db.update(
        "UPDATE inv_stocktake SET status='EXECUTED',approved_by=? WHERE id=?",
        access.current().id(),
        id);
    audit.record("EXECUTE", "STOCKTAKE", id, null);
    return ApiResponse.ok(null);
  }

  @PostMapping("/stocktakes/{id}/cancel")
  @Transactional
  ApiResponse<?> cancelCount(@PathVariable long id) {
    access.require("inventory:write");
    var t = db.queryForMap("SELECT * FROM inv_stocktake WHERE id=? FOR UPDATE", id);
    access.location(id(t.get("location_id")));
    InventoryService.requireState(text(t, "status"), "COUNTING", "SUBMITTED");
    db.update("UPDATE inv_stocktake SET status='CANCELLED' WHERE id=?", id);
    audit.record("CANCEL", "STOCKTAKE", id, null);
    return ApiResponse.ok(null);
  }

  @GetMapping("/closings")
  ApiResponse<?> closings(@RequestParam long locationId) {
    access.require("inventory:read");
    access.location(locationId);
    return ApiResponse.ok(
        db.queryForList(
            "SELECT * FROM inv_closing WHERE location_id=? ORDER BY period_end DESC", locationId));
  }

  @PostMapping("/closings")
  @Transactional
  ApiResponse<?> close(@RequestBody Map<String, Object> r)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    access.require("accounting:write");
    long location = id(r.get("locationId"));
    access.location(location);
    String type = text(r, "periodType");
    LocalDate date = LocalDate.parse(text(r, "date")), start, end;
    if (!Set.of("DAY", "MONTH", "YEAR").contains(type) || date.isAfter(LocalDate.now()))
      throw new BusinessException("VALIDATION_FAILED", "结账期间无效");
    start =
        type.equals("DAY")
            ? date
            : type.equals("MONTH") ? date.withDayOfMonth(1) : date.withDayOfYear(1);
    end =
        type.equals("DAY")
            ? date
            : type.equals("MONTH")
                ? date.withDayOfMonth(date.lengthOfMonth())
                : date.withDayOfYear(date.lengthOfYear());
    if (end.isAfter(LocalDate.now()))
      throw new BusinessException("VALIDATION_FAILED", "尚未结束的期间不能月结或年结");
    db.queryForMap("SELECT id FROM sys_location WHERE id=? FOR UPDATE", location);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM inv_stocktake WHERE location_id=? AND status IN"
                + " ('COUNTING','SUBMITTED')",
            Integer.class,
            location)
        > 0) throw new BusinessException("STOCK_FROZEN", "请先完成盘点");
    if (type.equals("MONTH")) {
      for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1))
        if (db.queryForObject(
                "SELECT COUNT(*) FROM inv_closing WHERE location_id=? AND period_type='DAY' AND"
                    + " period_start=? AND status='CLOSED'",
                Integer.class,
                location,
                day)
            == 0) throw new BusinessException("CLOSING_REQUIRED", "请先完成本月每日结账，包括无业务日期");
    }
    if (type.equals("YEAR")
        && db.queryForObject(
                "SELECT COUNT(*) FROM inv_closing WHERE location_id=? AND period_type='MONTH' AND"
                    + " period_start BETWEEN ? AND ? AND status='CLOSED'",
                Integer.class,
                location,
                start,
                end)
            != 12) throw new BusinessException("CLOSING_REQUIRED", "年结须先完成十二个月结");
    var snapshot =
        db.queryForList(
            "SELECT s.id,COALESCE(SUM(CASE WHEN l.business_date<? THEN l.qty_delta ELSE 0 END),0)"
                + " openingQty,COALESCE(SUM(CASE WHEN l.business_date BETWEEN ? AND ? AND"
                + " l.qty_delta>0 THEN l.qty_delta ELSE 0 END),0) incomingQty,COALESCE(SUM(CASE"
                + " WHEN l.business_date BETWEEN ? AND ? AND l.qty_delta<0 THEN -l.qty_delta ELSE 0"
                + " END),0) outgoingQty,COALESCE(SUM(l.qty_delta),0)"
                + " closingQty,COALESCE(SUM(l.cost_delta),0) closingCost FROM inv_stock s LEFT JOIN"
                + " inv_ledger l ON l.stock_id=s.id AND l.business_date<=? WHERE s.location_id=?"
                + " GROUP BY s.id ORDER BY s.id",
            start,
            start,
            end,
            start,
            end,
            end,
            location);
    if (end.equals(LocalDate.now()))
      for (var line : snapshot) {
        var balance =
            db.queryForMap(
                "SELECT on_hand_qty,remaining_cost FROM inv_stock WHERE id=? FOR UPDATE",
                line.get("id"));
        if (decimal(balance.get("on_hand_qty")).compareTo(decimal(line.get("closingQty"))) != 0
            || decimal(balance.get("remaining_cost")).compareTo(decimal(line.get("closingCost")))
                != 0) throw new BusinessException("RECONCILIATION_FAILED", "库存余额与流水不平，不能结账");
      }
    if (db.queryForObject(
            "SELECT COUNT(*) FROM inv_closing WHERE location_id=? AND period_type=? AND"
                + " period_start=? AND period_end=? AND status='CLOSED'",
            Integer.class,
            location,
            type,
            start,
            end)
        > 0) throw new BusinessException("ALREADY_CLOSED", "该期间已经结账");
    int revision =
        db.queryForObject(
            "SELECT COALESCE(MAX(revision),0)+1 FROM inv_closing WHERE location_id=? AND"
                + " period_type=? AND period_start=? AND period_end=?",
            Integer.class,
            location,
            type,
            start,
            end);
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_closing(id,location_id,period_type,period_start,period_end,snapshot_json,closed_by,revision)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        id,
        location,
        type,
        start,
        end,
        json.writeValueAsString(snapshot),
        access.current().id(),
        revision);
    audit.record("CLOSE", "INVENTORY", id, type);
    return ApiResponse.ok(Map.of("id", id));
  }

  @PostMapping("/closings/{id}/reopen")
  @Transactional
  ApiResponse<?> reopen(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("accounting:write");
    String reason = text(r, "reason");
    if (reason.isBlank()) throw new BusinessException("VALIDATION_FAILED", "重开须填写原因");
    var c = db.queryForMap("SELECT * FROM inv_closing WHERE id=? FOR UPDATE", id);
    long location = id(c.get("location_id"));
    access.location(location);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM inv_closing WHERE location_id=? AND id<>? AND status='CLOSED' AND"
                + " period_start<=? AND period_end>=? AND period_type<>?",
            Integer.class,
            location,
            id,
            c.get("period_start"),
            c.get("period_end"),
            c.get("period_type"))
        > 0) throw new BusinessException("PARENT_PERIOD_CLOSED", "须先重开上级月结或年结");
    db.update("UPDATE inv_closing SET status='REOPENED' WHERE id=?", id);
    audit.record("REOPEN", "CLOSING", id, reason);
    return ApiResponse.ok(null);
  }
}
