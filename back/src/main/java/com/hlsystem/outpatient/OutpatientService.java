package com.hlsystem.outpatient;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.clinical.ClinicalService;
import com.hlsystem.common.*;
import com.hlsystem.inventory.InventoryService;
import com.hlsystem.inventory.ReservationService;
import com.hlsystem.rational.RationalService;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutpatientService {
  private final ReservationService reservations;
  private final JdbcTemplate db;
  private final AccessService access;
  private final AuditService audit;
  private final InventoryService inventory;
  private final AccountingService accounting;
  private final RationalService rational;
  private final ClinicalService clinical;

  public OutpatientService(
      JdbcTemplate db,
      AccessService access,
      AuditService audit,
      InventoryService inventory,
      AccountingService accounting,
      RationalService rational,
      ClinicalService clinical,
      ReservationService reservations) {
    this.db = db;
    this.access = access;
    this.audit = audit;
    this.inventory = inventory;
    this.accounting = accounting;
    this.rational = rational;
    this.clinical = clinical;
    this.reservations = reservations;
  }

  public List<Map<String, Object>> list(long location) {
    access.location(location);
    String sql =
        "SELECT rx.*,p.name patientName,p.allergy_notes,e.number encounterNumber,u.display_name"
            + " doctor FROM op_prescription rx JOIN cl_encounter e ON e.id=rx.encounter_id JOIN"
            + " cl_patient p ON p.id=e.patient_id JOIN sys_user u ON u.id=rx.doctor_id WHERE"
            + " rx.location_id=?";
    if (access.current().roles().contains("DOCTOR") && !access.current().roles().contains("ADMIN"))
      return db.queryForList(
          sql + " AND rx.doctor_id=? ORDER BY rx.created_at DESC LIMIT 200",
          location,
          access.current().id());
    return db.queryForList(sql + " ORDER BY rx.created_at DESC LIMIT 200", location);
  }

  public Map<String, Object> detail(long id) {
    var rx = rx(id, false);
    access.location(id(rx.get("location_id")));
    if (access.current().roles().contains("DOCTOR") && !access.current().roles().contains("ADMIN"))
      clinical.encounterAccess(id(rx.get("encounter_id")));
    var result = new HashMap<>(rx);
    result.put("items", lines(id));
    result.put(
        "patient",
        db.queryForMap(
            "SELECT p.*,e.number encounterNumber FROM cl_patient p JOIN cl_encounter e ON"
                + " e.patient_id=p.id WHERE e.id=?",
            rx.get("encounter_id")));
    result.put(
        "checks",
        db.queryForList(
            "SELECT * FROM ru_check WHERE source_type='PRESCRIPTION' AND source_id=? ORDER BY"
                + " checked_at DESC LIMIT 10",
            id));
    result.put(
        "returns",
        db.queryForList(
            "SELECT r.*,i.name FROM ph_return_order r JOIN op_prescription_item x ON"
                + " x.id=r.prescription_item_id JOIN md_item i ON i.id=x.item_id WHERE"
                + " r.prescription_id=?",
            id));
    result.put(
        "actions",
        db.queryForList(
            "SELECT a.*,u.display_name operator FROM sys_document_action a JOIN sys_user u ON"
                + " u.id=a.operator_id WHERE a.source_type='PRESCRIPTION' AND a.source_id=? ORDER"
                + " BY a.occurred_at",
            id));
    return result;
  }

  @Transactional
  public long create(Map<String, Object> r) {
    access.require("clinical:write");
    long encounter = id(r.get("encounterId")), location = id(r.get("locationId"));
    clinical.encounterAccess(encounter);
    access.location(location);
    if (!"OUTPATIENT"
        .equals(
            db.queryForObject(
                "SELECT type FROM cl_encounter WHERE id=? AND status='ACTIVE'",
                String.class,
                encounter))) throw new BusinessException("VALIDATION_FAILED", "请选择有效门诊就诊");
    if (!(r.get("items") instanceof List<?> items) || items.isEmpty() || items.size() > 100)
      throw new BusinessException("VALIDATION_FAILED", "请填写药品明细");
    long rx = IdWorker.getId();
    db.update(
        "INSERT INTO op_prescription(id,number,encounter_id,doctor_id,location_id) VALUES"
            + " (?,?,?,?,?)",
        rx,
        "RX" + rx,
        encounter,
        access.current().id(),
        location);
    for (Object value : items) {
      var l = (Map<?, ?>) value;
      long item = id(l.get("itemId")), unit = id(l.get("unitId"));
      BigDecimal qty = decimal(l.get("quantity")), dose = decimal(l.get("dose"));
      positive(qty, "发药数量");
      positive(dose, "单次剂量");
      var data =
          db.queryForList(
              "SELECT iu.factor_to_base,i.quantity_scale FROM md_item_unit iu JOIN md_item i ON"
                  + " i.id=iu.item_id JOIN md_drug d ON d.item_id=i.id WHERE iu.item_id=? AND"
                  + " iu.unit_id=? AND i.enabled=TRUE",
              item,
              unit);
      if (data.isEmpty()) throw new BusinessException("VALIDATION_FAILED", "药品单位无效");
      BigDecimal factor = decimal(data.getFirst().get("factor_to_base")),
          base = qty.multiply(factor);
      if (base.stripTrailingZeros().scale()
          > ((Number) data.getFirst().get("quantity_scale")).intValue())
        throw new BusinessException("VALIDATION_FAILED", "数量不符合药品精度");
      int times = Integer.parseInt(l.get("frequencyPerDay").toString()),
          days = Integer.parseInt(l.get("days").toString());
      if (times < 1 || times > 24 || days < 1 || days > 90)
        throw new BusinessException("VALIDATION_FAILED", "频次或天数超出范围");
      db.update(
          "INSERT INTO"
              + " op_prescription_item(id,prescription_id,item_id,unit_id,quantity,factor_snapshot,base_qty,dose,dose_unit,frequency_per_day,days,route)"
              + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
          IdWorker.getId(),
          rx,
          item,
          unit,
          qty,
          factor,
          base,
          dose,
          l.get("doseUnit"),
          times,
          days,
          l.get("route"));
    }
    db.update(
        "UPDATE op_prescription_item x JOIN md_item i ON i.id=x.item_id JOIN md_unit u ON"
            + " u.id=x.unit_id SET"
            + " x.name_snapshot=i.name,x.specification_snapshot=i.specification,x.unit_snapshot=u.name"
            + " WHERE x.prescription_id=?",
        rx);
    audit.transition("PRESCRIPTION", rx, null, "DRAFT", "CREATE", null);
    return rx;
  }

  @Transactional
  public Map<String, Object> check(long id) {
    var rx = rx(id, true);
    access.location(id(rx.get("location_id")));
    var result = rational.check(id(rx.get("encounter_id")), lines(id), "PRESCRIPTION", id);
    db.update("UPDATE op_prescription SET check_status=? WHERE id=?", result.get("status"), id);
    return result;
  }

  @Transactional
  public void action(long id, String action, int version, String reason) {
    accounting.openPeriod();
    var rx = rx(id, true);
    long location = id(rx.get("location_id"));
    access.location(location);
    String from = text(rx, "status"), to;
    if (((Number) rx.get("version")).intValue() != version)
      throw new BusinessException("VERSION_CONFLICT", "处方已变化，请刷新", HttpStatus.CONFLICT);
    switch (action) {
      case "submit" -> {
        access.require("clinical:write");
        clinical.encounterAccess(id(rx.get("encounter_id")));
        InventoryService.requireState(from, "DRAFT");
        to = "SUBMITTED";
      }
      case "price" -> {
        access.require("outpatient:review");
        InventoryService.requireState(from, "SUBMITTED");
        var result = check(id);
        rational.assertAllowed(result, reason);
        BigDecimal amount = BigDecimal.ZERO;
        for (var line : lines(id)) {
          BigDecimal price =
              db.queryForObject(
                  "SELECT retail_price FROM md_drug WHERE item_id=? FOR UPDATE",
                  BigDecimal.class,
                  line.get("item_id"));
          BigDecimal lineAmount = money(price.multiply(decimal(line.get("base_qty"))));
          reservations.reserve(
              location, id(line.get("item_id")), id(line.get("id")), decimal(line.get("base_qty")));
          db.update(
              "UPDATE op_prescription_item SET unit_price=?,amount=? WHERE id=?",
              price,
              lineAmount,
              line.get("id"));
          amount = amount.add(lineAmount);
        }
        db.update(
            "UPDATE op_prescription SET total_amount=?,reviewed_by=?,warning_reason=? WHERE id=?",
            amount,
            access.current().id(),
            reason,
            id);
        db.update(
            "INSERT INTO cl_charge(id,encounter_id,source_type,source_id,amount) VALUES"
                + " (?,?,'PRESCRIPTION',?,?) ON DUPLICATE KEY UPDATE"
                + " amount=VALUES(amount),status='UNPAID'",
            IdWorker.getId(),
            rx.get("encounter_id"),
            id,
            amount);
        to = "PRICED";
      }
      case "pay" -> {
        access.require("clinical:payment");
        InventoryService.requireState(from, "PRICED");
        BigDecimal amount = decimal(rx.get("total_amount"));
        var charge =
            db.queryForMap(
                "SELECT * FROM cl_charge WHERE source_type='PRESCRIPTION' AND source_id=? FOR"
                    + " UPDATE",
                id);
        if (amount.signum() > 0) {
          long payment = IdWorker.getId();
          db.update(
              "INSERT INTO cl_payment(id,charge_id,encounter_id,kind,amount,operator_id) VALUES"
                  + " (?,?,?,'PAYMENT',?,?)",
              payment,
              charge.get("id"),
              rx.get("encounter_id"),
              amount,
              access.current().id());
          accounting.post("PAY:" + payment, "PAYMENT", payment, "门诊缴费", amount, 5, 7, location);
        }
        db.update(
            "UPDATE cl_charge SET paid_amount=amount,status='PAID' WHERE id=?", charge.get("id"));
        to = "PAID";
      }
      case "verify" -> {
        access.require("outpatient:review");
        InventoryService.requireState(from, "PAID");
        rational.assertAllowed(check(id), reason.isBlank() ? text(rx, "warning_reason") : reason);
        db.update(
            "UPDATE op_prescription SET verified_by=?,warning_reason=? WHERE id=?",
            access.current().id(),
            reason.isBlank() ? text(rx, "warning_reason") : reason,
            id);
        to = "VERIFIED";
      }
      case "dispense" -> {
        access.require("outpatient:dispense");
        InventoryService.requireState(from, "VERIFIED");
        if (id(rx.get("verified_by")) == access.current().id())
          throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "复核人与发药人必须不同");
        rational.assertAllowed(check(id), text(rx, "warning_reason"));
        reservations.release(id, "CONSUMED");
        long document = dispense(rx, lines(id), reason);
        db.update(
            "UPDATE op_prescription SET inventory_document_id=?,dispensed_by=? WHERE id=?",
            document,
            access.current().id(),
            id);
        accounting.post(
            "RX-INCOME:" + id,
            "PRESCRIPTION",
            id,
            "门诊发药收入",
            decimal(rx.get("total_amount")),
            7,
            6,
            location);
        to = "DISPENSED";
      }
      case "cancel" -> {
        access.require("clinical:write");
        clinical.encounterAccess(id(rx.get("encounter_id")));
        InventoryService.requireState(from, "DRAFT", "SUBMITTED", "PRICED", "PAID", "VERIFIED");
        if (Set.of("PAID", "VERIFIED").contains(from)) refundCancelled(rx);
        reservations.release(id, "RELEASED");
        to = "CANCELLED";
      }
      default -> throw new BusinessException("VALIDATION_FAILED", "操作不存在");
    }
    db.update("UPDATE op_prescription SET status=?,version=version+1 WHERE id=?", to, id);
    audit.transition("PRESCRIPTION", id, from, to, action.toUpperCase(), reason);
  }

  private long dispense(Map<String, Object> rx, List<Map<String, Object>> lines, String recipient) {
    long location = id(rx.get("location_id"));
    long doc = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason,executed_by,executed_at)"
            + " VALUES (?,?,'DISPENSE',?,'EXECUTED',?,?,?, ?,CURRENT_TIMESTAMP(3))",
        doc,
        "DSP" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        recipient,
        access.current().id());
    BigDecimal totalCost = BigDecimal.ZERO;
    for (var line : lines) {
      long item = id(line.get("item_id")), invLine = IdWorker.getId();
      String control =
          db.queryForObject(
              "SELECT control_level FROM md_drug WHERE item_id=? FOR UPDATE", String.class, item);
      if (!control.equals("NORMAL") && recipient.isBlank())
        throw new BusinessException("CONTROL_REGISTER_REQUIRED", "控制药品必须填写领药人及用途");
      db.update(
          "INSERT INTO"
              + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty)"
              + " VALUES (?,?,?,?,?,?,?)",
          invLine,
          doc,
          item,
          line.get("unit_id"),
          line.get("quantity"),
          line.get("factor_snapshot"),
          line.get("base_qty"));
      var allocations =
          inventory.remove(location, item, decimal(line.get("base_qty")), invLine, "DISPENSE");
      BigDecimal cost =
          allocations.stream()
              .map(a -> decimal(a.get("cost_amount")))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      totalCost = totalCost.add(cost);
      db.update("UPDATE inv_document_item SET cost_amount=? WHERE id=?", cost, invLine);
      db.update(
          "UPDATE op_prescription_item SET inventory_item_id=? WHERE id=?",
          invLine,
          line.get("id"));
      if (!control.equals("NORMAL"))
        db.update(
            "INSERT INTO"
                + " inv_control_register(id,document_item_id,patient_id,recipient,reviewer_id,executor_id)"
                + " VALUES (?,?,?,?,?,?)",
            IdWorker.getId(),
            invLine,
            db.queryForObject(
                "SELECT patient_id FROM cl_encounter WHERE id=?",
                Long.class,
                rx.get("encounter_id")),
            recipient,
            rx.get("verified_by"),
            access.current().id());
    }
    accounting.post(
        "RX-COST:" + rx.get("id"),
        "PRESCRIPTION",
        id(rx.get("id")),
        "门诊发药成本",
        totalCost,
        3,
        1,
        location);
    return doc;
  }

  private void refundCancelled(Map<String, Object> rx) {
    access.require("clinical:payment");
    var charge =
        db.queryForMap(
            "SELECT * FROM cl_charge WHERE source_type='PRESCRIPTION' AND source_id=? FOR UPDATE",
            rx.get("id"));
    BigDecimal amount =
        decimal(charge.get("paid_amount")).subtract(decimal(charge.get("refunded_amount")));
    if (amount.signum() > 0) {
      long payment = IdWorker.getId();
      db.update(
          "INSERT INTO cl_payment(id,charge_id,encounter_id,kind,amount,operator_id,reason) VALUES"
              + " (?,?,?,'REFUND',?,?,?)",
          payment,
          charge.get("id"),
          rx.get("encounter_id"),
          amount,
          access.current().id(),
          "取消未发处方");
      accounting.post(
          "REFUND:" + payment,
          "PAYMENT",
          payment,
          "未发药退款",
          amount,
          7,
          5,
          id(rx.get("location_id")));
    }
    db.update(
        "UPDATE cl_charge SET refunded_amount=paid_amount,status='CANCELLED' WHERE id=?",
        charge.get("id"));
  }

  @Transactional
  public long returnDrug(long prescriptionId, Map<String, Object> r) {
    access.require("outpatient:review");
    accounting.openPeriod();
    var rx = rx(prescriptionId, true);
    access.location(id(rx.get("location_id")));
    InventoryService.requireState(text(rx, "status"), "DISPENSED");
    long lineId = id(r.get("itemId"));
    var lines =
        db.queryForList(
            "SELECT * FROM op_prescription_item WHERE id=? AND prescription_id=? FOR UPDATE",
            lineId,
            prescriptionId);
    if (lines.isEmpty()) throw new BusinessException("NOT_FOUND", "处方药品不存在", HttpStatus.NOT_FOUND);
    var line = lines.getFirst();
    BigDecimal qty = decimal(r.get("quantity"));
    positive(qty, "退药基础数量");
    BigDecimal remaining =
        decimal(line.get("base_qty")).subtract(decimal(line.get("returned_base_qty")));
    if (qty.compareTo(remaining) > 0)
      throw new BusinessException("RETURN_LIMIT_EXCEEDED", "退药数量超过剩余可退数量");
    String reason = text(r, "reason");
    if (reason.isBlank()) throw new BusinessException("VALIDATION_FAILED", "请填写退药原因");
    String disposition = text(r, "qualityDisposition");
    if (!Set.of("AVAILABLE", "QUARANTINE").contains(disposition))
      throw new BusinessException("VALIDATION_FAILED", "退回质量状态无效");
    long returnId = IdWorker.getId(), doc = IdWorker.getId(), invLine = IdWorker.getId();
    long location = id(rx.get("location_id"));
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'RETURN',?,'EXECUTED',?,?,?)",
        doc,
        "RET" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        reason);
    db.update(
        "INSERT INTO"
            + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty)"
            + " VALUES (?,?,?,?,?,1,?)",
        invLine,
        doc,
        line.get("item_id"),
        db.queryForObject(
            "SELECT base_unit_id FROM md_item WHERE id=?", Long.class, line.get("item_id")),
        qty,
        qty);
    BigDecimal left = qty, totalCost = BigDecimal.ZERO;
    for (var a :
        db.queryForList(
            "SELECT * FROM inv_allocation WHERE document_item_id=? ORDER BY id FOR UPDATE",
            line.get("inventory_item_id"))) {
      if (left.signum() == 0) break;
      BigDecimal avail = decimal(a.get("base_qty")).subtract(decimal(a.get("returned_qty"))),
          take = left.min(avail);
      if (take.signum() == 0) continue;
      BigDecimal cost =
          take.compareTo(avail) == 0
              ? decimal(a.get("cost_amount")).subtract(decimal(a.get("returned_cost")))
              : money(
                  decimal(a.get("cost_amount"))
                      .multiply(take)
                      .divide(decimal(a.get("base_qty")), 8, RoundingMode.HALF_UP));
      inventory.add(
          location,
          id(a.get("cost_layer_id")),
          disposition,
          take,
          cost,
          invLine,
          "RETURN:" + returnId + ":" + a.get("id"));
      db.update(
          "UPDATE inv_allocation SET returned_qty=returned_qty+?,returned_cost=returned_cost+?"
              + " WHERE id=?",
          take,
          cost,
          a.get("id"));
      totalCost = totalCost.add(cost);
      left = left.subtract(take);
    }
    db.update(
        "UPDATE op_prescription_item SET returned_base_qty=returned_base_qty+? WHERE id=?",
        qty,
        lineId);
    BigDecimal amount = money(qty.multiply(decimal(line.get("unit_price"))));
    db.update(
        "INSERT INTO"
            + " ph_return_order(id,prescription_id,prescription_item_id,base_qty,amount,quality_disposition,reason,inventory_document_id,created_by)"
            + " VALUES (?,?,?,?,?,?,?,?,?)",
        returnId,
        prescriptionId,
        lineId,
        qty,
        amount,
        disposition,
        reason,
        doc,
        access.current().id());
    accounting.post(
        "RETURN-COST:" + returnId, "RETURN", returnId, "退药恢复成本", totalCost, 1, 3, location);
    audit.record("RETURN", "PRESCRIPTION", prescriptionId, reason);
    return returnId;
  }

  @Transactional
  public void refund(long returnId) {
    access.require("clinical:payment");
    accounting.openPeriod();
    var rows = db.queryForList("SELECT * FROM ph_return_order WHERE id=?", returnId);
    if (rows.isEmpty()) throw new BusinessException("NOT_FOUND", "退药单不存在", HttpStatus.NOT_FOUND);
    var rx = rx(id(rows.getFirst().get("prescription_id")), true);
    access.location(id(rx.get("location_id")));
    var ret = db.queryForMap("SELECT * FROM ph_return_order WHERE id=? FOR UPDATE", returnId);
    InventoryService.requireState(text(ret, "refund_status"), "PENDING");
    var charge =
        db.queryForMap(
            "SELECT * FROM cl_charge WHERE source_type='PRESCRIPTION' AND source_id=? FOR UPDATE",
            rx.get("id"));
    BigDecimal amount = decimal(ret.get("amount"));
    if (amount.compareTo(
            decimal(charge.get("paid_amount")).subtract(decimal(charge.get("refunded_amount"))))
        > 0) throw new BusinessException("REFUND_LIMIT_EXCEEDED", "退款超过原收款");
    if (amount.signum() > 0) {
      long payment = IdWorker.getId();
      db.update(
          "INSERT INTO cl_payment(id,charge_id,encounter_id,kind,amount,operator_id) VALUES"
              + " (?,?,?,'REFUND',?,?)",
          payment,
          charge.get("id"),
          rx.get("encounter_id"),
          amount,
          access.current().id());
      accounting.post(
          "RETURN-REFUND:" + returnId,
          "RETURN",
          returnId,
          "退药退款",
          amount,
          6,
          5,
          id(rx.get("location_id")));
    }
    db.update(
        "UPDATE cl_charge SET refunded_amount=refunded_amount+? WHERE id=?",
        amount,
        charge.get("id"));
    db.update("UPDATE ph_return_order SET refund_status='REFUNDED' WHERE id=?", returnId);
    audit.record("REFUND", "RETURN", returnId, null);
  }

  private List<Map<String, Object>> lines(long rx) {
    return db.queryForList(
        "SELECT l.*,COALESCE(l.name_snapshot,i.name)"
            + " name,COALESCE(l.specification_snapshot,i.specification)"
            + " specification,COALESCE(l.unit_snapshot,u.name) unit,d.control_level FROM"
            + " op_prescription_item l JOIN md_item i ON i.id=l.item_id JOIN md_unit u ON"
            + " u.id=l.unit_id JOIN md_drug d ON d.item_id=i.id WHERE l.prescription_id=? ORDER BY"
            + " l.item_id,l.id",
        rx);
  }

  private Map<String, Object> rx(long id, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT * FROM op_prescription WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (rows.isEmpty()) throw new BusinessException("NOT_FOUND", "处方不存在", HttpStatus.NOT_FOUND);
    return rows.getFirst();
  }
}
