package com.hlsystem.accounting;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountingController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final AccountingService accounting;
  private final AuditService audit;
  private final PriceService prices;

  public AccountingController(
      JdbcTemplate db,
      AccessService access,
      AccountingService accounting,
      AuditService audit,
      PriceService prices) {
    this.db = db;
    this.access = access;
    this.accounting = accounting;
    this.audit = audit;
    this.prices = prices;
  }

  @GetMapping("/accounting/vouchers")
  ApiResponse<?> vouchers() {
    access.require("accounting:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT v.*,SUM(e.debit) debit,SUM(e.credit) credit FROM acc_voucher v JOIN"
                + " acc_voucher_entry e ON e.voucher_id=v.id GROUP BY v.id ORDER BY v.created_at"
                + " DESC LIMIT 200"));
  }

  @GetMapping("/accounting/vouchers/{id}")
  ApiResponse<?> entries(@PathVariable long id) {
    access.require("accounting:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT e.*,a.name account,l.name location FROM acc_voucher_entry e JOIN acc_account a"
                + " ON a.id=e.account_id LEFT JOIN sys_location l ON l.id=e.location_id WHERE"
                + " e.voucher_id=? ORDER BY e.id",
            id));
  }

  @GetMapping("/accounting/payables")
  ApiResponse<?> payables() {
    access.require("accounting:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT p.*,s.name supplier,d.number documentNumber,p.amount-p.paid_amount remaining"
                + " FROM acc_payable p JOIN md_supplier s ON s.id=p.supplier_id JOIN inv_document d"
                + " ON d.id=p.document_id ORDER BY p.id DESC"));
  }

  @PostMapping("/accounting/payables/{id}/pay")
  @Transactional
  ApiResponse<?> pay(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("accounting:write");
    accounting.openPeriod();
    var p = db.queryForMap("SELECT * FROM acc_payable WHERE id=? FOR UPDATE", id);
    BigDecimal amount = decimal(r.get("amount"));
    positive(amount, "付款金额");
    if (amount.compareTo(decimal(p.get("amount")).subtract(decimal(p.get("paid_amount")))) > 0)
      throw new BusinessException("PAYMENT_LIMIT_EXCEEDED", "付款超过应付余额");
    long payment = IdWorker.getId();
    db.update(
        "INSERT INTO acc_supplier_payment(id,payable_id,amount,operator_id) VALUES (?,?,?,?)",
        payment,
        id,
        amount,
        access.current().id());
    db.update("UPDATE acc_payable SET paid_amount=paid_amount+? WHERE id=?", amount, id);
    accounting.post("SUPPLIER-PAY:" + payment, "PAYMENT", payment, "供应商付款", amount, 2, 5, null);
    audit.record("PAY", "PAYABLE", id, null);
    return ApiResponse.ok(null);
  }

  @GetMapping("/accounting/reconciliation")
  ApiResponse<?> reconciliation() {
    access.require("accounting:read");
    return ApiResponse.ok(
        Map.of(
            "voucherDifference",
                db.queryForObject(
                    "SELECT COALESCE(SUM(debit-credit),0) FROM acc_voucher_entry",
                    BigDecimal.class),
            "inventoryCost",
                db.queryForObject(
                    "SELECT COALESCE(SUM(remaining_cost),0) FROM inv_stock", BigDecimal.class),
            "inventoryLedgerCost",
                db.queryForObject(
                    "SELECT COALESCE(SUM(cost_delta),0) FROM inv_ledger", BigDecimal.class),
            "prescriptions",
                db.queryForList(
                    "SELECT"
                        + " rx.number,rx.status,rx.total_amount,c.paid_amount,c.refunded_amount,CASE"
                        + " WHEN rx.status IN ('PAID','VERIFIED') THEN '已缴未发' WHEN"
                        + " rx.status='DISPENSED' AND EXISTS (SELECT 1 FROM ph_return_order r WHERE"
                        + " r.prescription_id=rx.id AND r.refund_status='PENDING') THEN '退药待退款'"
                        + " ELSE '正常' END difference FROM op_prescription rx LEFT JOIN cl_charge c"
                        + " ON c.source_type='PRESCRIPTION' AND c.source_id=rx.id ORDER BY"
                        + " rx.created_at DESC LIMIT 200")));
  }

  @GetMapping("/pricing/adjustments")
  ApiResponse<?> adjustments() {
    access.require("accounting:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT a.*,i.name,i.specification,u.display_name creator FROM price_adjustment a JOIN"
                + " md_item i ON i.id=a.item_id JOIN sys_user u ON u.id=a.created_by ORDER BY"
                + " a.created_at DESC"));
  }

  @PostMapping("/pricing/adjustments")
  ApiResponse<?> createPrice(@RequestBody Map<String, Object> r) {
    return ApiResponse.ok(Map.of("id", prices.create(r)));
  }

  @PostMapping("/pricing/adjustments/{id}/confirm")
  ApiResponse<?> confirmPrice(@PathVariable long id) {
    return ApiResponse.ok(prices.apply(id));
  }

  @PostMapping("/pricing/adjustments/{id}/approve")
  ApiResponse<?> approvePrice(@PathVariable long id) {
    prices.approve(id);
    return ApiResponse.ok(null);
  }
}
