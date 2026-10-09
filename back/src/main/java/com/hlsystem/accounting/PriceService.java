package com.hlsystem.accounting;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.*;
import com.hlsystem.inventory.InventoryService;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceService {
  private final JdbcTemplate db;
  private final AccessService access;
  private final AccountingService accounting;
  private final AuditService audit;

  public PriceService(
      JdbcTemplate db, AccessService access, AccountingService accounting, AuditService audit) {
    this.db = db;
    this.access = access;
    this.accounting = accounting;
    this.audit = audit;
  }

  @Transactional
  public long create(Map<String, Object> r) {
    access.require("pricing:write");
    long item = id(r.get("itemId"));
    BigDecimal price = decimal(r.get("newPrice"));
    if (price.signum() < 0 || price.scale() > 6)
      throw new BusinessException("VALIDATION_FAILED", "新价格无效");
    String reason = text(r, "reason"), mode = text(r, "confirmationMode");
    if (mode.isBlank()) mode = "MANUAL";
    if (reason.isBlank() || !java.util.Set.of("MANUAL", "AUTO").contains(mode))
      throw new BusinessException("VALIDATION_FAILED", "请填写调价原因及确认模式");
    LocalDateTime effective =
        text(r, "effectiveAt").isBlank()
            ? LocalDateTime.now()
            : LocalDateTime.parse(text(r, "effectiveAt").replace(' ', 'T'));
    if (mode.equals("AUTO") && effective.isBefore(LocalDateTime.now()))
      throw new BusinessException("VALIDATION_FAILED", "自动调价生效时间须在未来");
    BigDecimal old =
        db.queryForObject(
            "SELECT retail_price FROM md_drug WHERE item_id=?", BigDecimal.class, item);
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " price_adjustment(id,number,item_id,old_price,new_price,reason,created_by,effective_at,confirmation_mode)"
            + " VALUES (?,?,?,?,?,?,?,?,?)",
        id,
        "PA" + id,
        item,
        old,
        price,
        reason,
        access.current().id(),
        effective,
        mode);
    audit.record("CREATE", "PRICE", id, reason);
    return id;
  }

  @Transactional
  public void approve(long id) {
    access.require("pricing:write");
    var p = db.queryForMap("SELECT * FROM price_adjustment WHERE id=? FOR UPDATE", id);
    InventoryService.requireState(text(p, "status"), "SUBMITTED");
    if (!text(p, "confirmation_mode").equals("AUTO"))
      throw new BusinessException("INVALID_STATE", "手动调价请直接确认");
    independent(p);
    db.update(
        "UPDATE price_adjustment SET status='APPROVED',approved_by=? WHERE id=?",
        access.current().id(),
        id);
    audit.record("APPROVE", "PRICE", id, null);
  }

  @Transactional
  public Map<String, Object> apply(long id) {
    access.require("pricing:write");
    accounting.openPeriod();
    var p = db.queryForMap("SELECT * FROM price_adjustment WHERE id=? FOR UPDATE", id);
    boolean auto = text(p, "confirmation_mode").equals("AUTO");
    InventoryService.requireState(text(p, "status"), auto ? "APPROVED" : "SUBMITTED");
    if (!auto) independent(p);
    if (auto
        && LocalDateTime.parse(p.get("effective_at").toString().replace(' ', 'T'))
            .isAfter(LocalDateTime.now()))
      throw new BusinessException("INVALID_STATE", "尚未到自动调价生效时间");
    var drug = db.queryForMap("SELECT * FROM md_drug WHERE item_id=? FOR UPDATE", p.get("item_id"));
    if (decimal(drug.get("retail_price")).compareTo(decimal(p.get("old_price"))) != 0)
      throw new BusinessException("VERSION_CONFLICT", "价格已变化，请重新制单");
    BigDecimal delta = BigDecimal.ZERO;
    for (var stock :
        db.queryForList(
            "SELECT s.* FROM inv_stock s JOIN inv_cost_layer c ON c.id=s.cost_layer_id JOIN"
                + " inv_batch b ON b.id=c.batch_id WHERE b.item_id=? ORDER BY s.id FOR UPDATE",
            p.get("item_id"))) {
      BigDecimal difference =
          money(
              decimal(stock.get("on_hand_qty"))
                  .multiply(decimal(p.get("new_price")).subtract(decimal(p.get("old_price")))));
      delta = delta.add(difference);
      db.update(
          "INSERT INTO price_valuation_detail VALUES (?,?,?,?,?)",
          IdWorker.getId(),
          id,
          stock.get("id"),
          stock.get("on_hand_qty"),
          difference);
    }
    db.update(
        "UPDATE md_drug SET retail_price=?,price_version=price_version+1 WHERE id=?",
        p.get("new_price"),
        drug.get("id"));
    db.update(
        "INSERT INTO price_history(id,item_id,adjustment_id,old_price,new_price) VALUES"
            + " (?,?,?,?,?)",
        IdWorker.getId(),
        p.get("item_id"),
        id,
        p.get("old_price"),
        p.get("new_price"));
    db.update(
        "UPDATE price_adjustment SET"
            + " status='EXECUTED',approved_by=COALESCE(approved_by,?),valuation_delta=? WHERE id=?",
        access.current().id(),
        delta,
        id);
    audit.record("CONFIRM", "PRICE", id, text(p, "reason"));
    return Map.of("valuationDelta", delta);
  }

  private void independent(Map<String, Object> p) {
    if (id(p.get("created_by")) == access.current().id())
      throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "制单人与调价确认人须不同");
  }
}
