package com.hlsystem.reporting;

import com.hlsystem.common.ApiResponse;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports/analysis")
public class AnalysisController {
  private final JdbcTemplate db;
  private final AccessService access;

  public AnalysisController(JdbcTemplate db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  @GetMapping
  ApiResponse<?> analysis(@RequestParam String from, @RequestParam String to) {
    access.require("accounting:read");
    LocalDate start = LocalDate.parse(from), end = LocalDate.parse(to);
    var income =
        db.queryForObject(
            "SELECT COALESCE(SUM(e.credit-e.debit),0) FROM acc_voucher_entry e JOIN acc_voucher v"
                + " ON v.id=e.voucher_id WHERE e.account_id=6 AND v.status='POSTED' AND"
                + " v.business_date BETWEEN ? AND ?",
            BigDecimal.class,
            start,
            end);
    var cost =
        db.queryForObject(
            "SELECT COALESCE(SUM(e.debit-e.credit),0) FROM acc_voucher_entry e JOIN acc_voucher v"
                + " ON v.id=e.voucher_id WHERE e.account_id=3 AND v.status='POSTED' AND"
                + " v.business_date BETWEEN ? AND ? AND v.source_type IN"
                + " ('PRESCRIPTION','EXECUTION','RETURN','SALE')",
            BigDecimal.class,
            start,
            end);
    String rate =
        cost.signum() > 0
            ? income
                .subtract(cost)
                .divide(cost, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
            : "不适用";
    var department =
        db.queryForList(
            "SELECT d.name department,COUNT(rx.id)"
                + " prescriptionCount,COALESCE(SUM(rx.total_amount-COALESCE(ret.amount,0)),0)"
                + " consumptionAmount FROM sys_department d LEFT JOIN cl_encounter en ON"
                + " en.department_id=d.id LEFT JOIN op_prescription rx ON rx.encounter_id=en.id AND"
                + " rx.status='DISPENSED' AND DATE(rx.created_at) BETWEEN ? AND ? LEFT JOIN (SELECT"
                + " prescription_id,SUM(amount) amount FROM ph_return_order GROUP BY"
                + " prescription_id) ret ON ret.prescription_id=rx.id GROUP BY d.id,d.name ORDER BY"
                + " consumptionAmount DESC",
            start,
            end);
    var production =
        db.queryForList(
            "SELECT"
                + " p.number,p.batch_no,f.name,p.good_qty,p.material_cost,p.labor_cost,p.energy_cost,p.other_cost,p.abnormal_loss_cost,p.unit_cost"
                + " FROM prep_production_order p JOIN prep_formula f ON f.id=p.formula_id WHERE"
                + " p.status='COMPLETED' AND DATE(p.created_at) BETWEEN ? AND ? ORDER BY"
                + " p.created_at DESC",
            start,
            end);
    var months =
        db.queryForList(
            "SELECT DATE_FORMAT(v.business_date,'%Y-%m') month,SUM(CASE WHEN e.account_id=6 THEN"
                + " e.credit-e.debit ELSE 0 END) income,SUM(CASE WHEN e.account_id=3 THEN"
                + " e.debit-e.credit ELSE 0 END) cost FROM acc_voucher v JOIN acc_voucher_entry e"
                + " ON e.voucher_id=v.id WHERE v.status='POSTED' AND v.business_date BETWEEN ? AND"
                + " ? AND e.account_id IN (3,6) GROUP BY DATE_FORMAT(v.business_date,'%Y-%m') ORDER"
                + " BY month",
            start, end);
    return ApiResponse.ok(
        Map.of(
            "income",
            income,
            "cost",
            cost,
            "markupRate",
            rate,
            "departments",
            department,
            "production",
            production,
            "months",
            months));
  }
}
