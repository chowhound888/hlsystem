package com.hlsystem.accounting;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting")
public class ManualVoucherController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final AccountingService accounting;
  private final AuditService audit;

  public ManualVoucherController(
      JdbcTemplate db, AccessService access, AccountingService accounting, AuditService audit) {
    this.db = db;
    this.access = access;
    this.accounting = accounting;
    this.audit = audit;
  }

  @GetMapping("/accounts")
  ApiResponse<?> accounts() {
    access.require("accounting:read");
    return ApiResponse.ok(db.queryForList("SELECT * FROM acc_account ORDER BY id"));
  }

  @PostMapping("/vouchers")
  @Transactional
  ApiResponse<?> create(@RequestBody Map<String, Object> r) {
    access.require("accounting:write");
    accounting.openPeriod();
    if (text(r, "description").isBlank()
        || !(r.get("entries") instanceof List<?> entries)
        || entries.size() < 2) throw new BusinessException("VALIDATION_FAILED", "请填写摘要及至少两条分录");
    BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
    for (Object value : entries) {
      var e = (Map<?, ?>) value;
      BigDecimal d = decimal(e.get("debit")), c = decimal(e.get("credit"));
      if (d.signum() < 0
          || c.signum() < 0
          || d.scale() > 2
          || c.scale() > 2
          || (d.signum() > 0 && c.signum() > 0)
          || (d.signum() == 0 && c.signum() == 0))
        throw new BusinessException("VALIDATION_FAILED", "分录须一侧为正数，金额最多2位小数");
      debit = debit.add(d);
      credit = credit.add(c);
    }
    if (debit.compareTo(credit) != 0) throw new BusinessException("VOUCHER_UNBALANCED", "凭证借贷不平衡");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " acc_voucher(id,number,event_key,source_type,source_id,business_date,status,description,created_by)"
            + " VALUES (?,?,?,'MANUAL',?,?,'DRAFT',?,?)",
        id,
        "MV" + id,
        "MANUAL:" + id,
        id,
        LocalDate.now(),
        text(r, "description"),
        access.current().id());
    for (Object value : entries) {
      var e = (Map<?, ?>) value;
      Long location = e.get("locationId") == null ? null : id(e.get("locationId"));
      if (location != null) access.location(location);
      db.update(
          "INSERT INTO acc_voucher_entry VALUES (?,?,?,?,?,?)",
          IdWorker.getId(),
          id,
          id(e.get("accountId")),
          location,
          decimal(e.get("debit")),
          decimal(e.get("credit")));
    }
    audit.record("CREATE", "VOUCHER", id, null);
    return ApiResponse.ok(Map.of("id", id));
  }

  @PostMapping("/vouchers/{id}/post")
  @Transactional
  ApiResponse<?> post(@PathVariable long id) {
    access.require("accounting:write");
    accounting.openPeriod();
    var v = db.queryForMap("SELECT * FROM acc_voucher WHERE id=? FOR UPDATE", id);
    com.hlsystem.inventory.InventoryService.requireState(text(v, "status"), "DRAFT");
    if (v.get("created_by") != null && id(v.get("created_by")) == access.current().id())
      throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "凭证制单与过账人须不同");
    BigDecimal difference =
        db.queryForObject(
            "SELECT SUM(debit-credit) FROM acc_voucher_entry WHERE voucher_id=?",
            BigDecimal.class,
            id);
    if (difference == null || difference.signum() != 0)
      throw new BusinessException("VOUCHER_UNBALANCED", "凭证借贷不平衡");
    db.update(
        "UPDATE acc_voucher SET status='POSTED',posted_by=? WHERE id=?", access.current().id(), id);
    audit.record("POST", "VOUCHER", id, null);
    return ApiResponse.ok(null);
  }
}
