package com.hlsystem.inpatient;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.clinical.ClinicalService;
import com.hlsystem.common.*;
import com.hlsystem.inventory.InventoryService;
import com.hlsystem.rational.RationalService;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InpatientService {
  private final JdbcTemplate db;
  private final AccessService access;
  private final ClinicalService clinical;
  private final AccountingService accounting;
  private final InventoryService inventory;
  private final RationalService rational;
  private final AuditService audit;
  private final ObjectMapper json;

  public InpatientService(
      JdbcTemplate db,
      AccessService access,
      ClinicalService clinical,
      AccountingService accounting,
      InventoryService inventory,
      RationalService rational,
      AuditService audit,
      ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.clinical = clinical;
    this.accounting = accounting;
    this.inventory = inventory;
    this.rational = rational;
    this.audit = audit;
    this.json = json;
  }

  @Transactional
  public long create(Map<String, Object> r) {
    access.require("clinical:write");
    long encounter = id(r.get("encounterId")),
        location = id(r.get("locationId")),
        item = id(r.get("itemId"));
    clinical.encounterAccess(encounter);
    access.location(location);
    if (!"INPATIENT"
        .equals(
            db.queryForObject(
                "SELECT type FROM cl_encounter WHERE id=? AND status='ACTIVE'",
                String.class,
                encounter))) throw new BusinessException("VALIDATION_FAILED", "请选择有效住院记录");
    LocalDate start = LocalDate.parse(text(r, "startDate"));
    LocalDate end = text(r, "endDate").isBlank() ? null : LocalDate.parse(text(r, "endDate"));
    if (end != null && end.isBefore(start))
      throw new BusinessException("VALIDATION_FAILED", "结束日期不能早于开始日期");
    String type = text(r, "orderType");
    if (!Set.of("TEMPORARY", "LONG_TERM", "HERBAL").contains(type))
      throw new BusinessException("VALIDATION_FAILED", "医嘱类型无效");
    BigDecimal qty = decimal(r.get("quantity")), dose = decimal(r.get("dose"));
    positive(qty, "每次基础数量");
    positive(dose, "剂量");
    int interval = Integer.parseInt(r.get("intervalDays").toString());
    if (interval < 1 || interval > 30)
      throw new BusinessException("VALIDATION_FAILED", "执行间隔范围为1至30天");
    if (!(r.get("times") instanceof List<?> times) || times.isEmpty() || times.size() > 12)
      throw new BusinessException("VALIDATION_FAILED", "请填写执行时段");
    for (Object t : times) LocalTime.parse(t.toString());
    long id = IdWorker.getId();
    try {
      db.update(
          "INSERT INTO"
              + " ip_medical_order(id,number,encounter_id,item_id,location_id,doctor_id,order_type,start_date,end_date,interval_days,times_json,base_qty,dose,dose_unit,route)"
              + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          id,
          "MO" + id,
          encounter,
          item,
          location,
          access.current().id(),
          type,
          start,
          end,
          interval,
          json.writeValueAsString(times),
          qty,
          dose,
          text(r, "doseUnit"),
          text(r, "route"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    audit.record("CREATE", "MEDICAL_ORDER", id, null);
    return id;
  }

  @Transactional
  public void generate(long location, LocalDate date) {
    access.require("inpatient:write");
    access.location(location);
    for (var order :
        db.queryForList(
            "SELECT * FROM ip_medical_order WHERE location_id=? AND status='ACTIVE' AND"
                + " start_date<=? AND (end_date IS NULL OR end_date>=?) ORDER BY id FOR UPDATE",
            location,
            date,
            date)) {
      LocalDate start = LocalDate.parse(order.get("start_date").toString());
      if (text(order, "order_type").equals("TEMPORARY") && !date.equals(start)) continue;
      if (ChronoUnit.DAYS.between(start, date) % ((Number) order.get("interval_days")).intValue()
          != 0) continue;
      try {
        List<?> times = json.readValue(order.get("times_json").toString(), List.class);
        for (Object t : times)
          db.update(
              "INSERT IGNORE INTO ip_order_execution(id,order_id,scheduled_at) VALUES (?,?,?)",
              IdWorker.getId(),
              order.get("id"),
              date.atTime(LocalTime.parse(t.toString())));
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
    audit.record("GENERATE", "EXECUTION", location, date.toString());
  }

  @Transactional
  public void stop(long id) {
    access.require("clinical:write");
    var order = db.queryForMap("SELECT * FROM ip_medical_order WHERE id=? FOR UPDATE", id);
    clinical.encounterAccess(id(order.get("encounter_id")));
    if (id(order.get("doctor_id")) != access.current().id()
        && !access.current().roles().contains("ADMIN"))
      throw new BusinessException("FORBIDDEN", "只能停止本人医嘱", HttpStatus.FORBIDDEN);
    db.update(
        "UPDATE ip_medical_order SET status='STOPPED',stopped_at=CURRENT_TIMESTAMP(3) WHERE id=?",
        id);
    for (var ex :
        db.queryForList(
            "SELECT * FROM ip_order_execution WHERE order_id=? AND status IN ('CONFIRMED','PICKED')"
                + " FOR UPDATE",
            id)) cancelExecution(ex, order);
    db.update(
        "UPDATE ip_order_execution SET status='CANCELLED' WHERE order_id=? AND status='PENDING'",
        id);
    audit.record("STOP", "MEDICAL_ORDER", id, null);
  }

  @Transactional
  public void action(long id, String action, String reason) {
    access.require("inpatient:write");
    accounting.openPeriod();
    var ex = db.queryForMap("SELECT * FROM ip_order_execution WHERE id=? FOR UPDATE", id);
    var order =
        db.queryForMap("SELECT * FROM ip_medical_order WHERE id=? FOR UPDATE", ex.get("order_id"));
    long location = id(order.get("location_id"));
    access.location(location);
    String status = text(ex, "status");
    InventoryService.requireState(text(order, "status"), "ACTIVE");
    switch (action) {
      case "confirm" -> {
        InventoryService.requireState(status, "PENDING");
        var checks =
            rational.check(
                id(order.get("encounter_id")),
                List.of(
                    Map.of(
                        "item_id",
                        order.get("item_id"),
                        "dose",
                        order.get("dose"),
                        "dose_unit",
                        order.get("dose_unit"),
                        "frequency_per_day",
                        1)),
                "EXECUTION",
                id);
        rational.assertAllowed(checks, reason);
        BigDecimal
            price =
                db.queryForObject(
                    "SELECT retail_price FROM md_drug WHERE item_id=? FOR UPDATE",
                    BigDecimal.class,
                    order.get("item_id")),
            amount = money(price.multiply(decimal(order.get("base_qty"))));
        var account =
            db.queryForMap(
                "SELECT * FROM cl_deposit_account WHERE encounter_id=? FOR UPDATE",
                order.get("encounter_id"));
        if (decimal(account.get("balance")).compareTo(amount) < 0) {
          if (!access.current().roles().contains("ADMIN"))
            throw new BusinessException("DEPOSIT_INSUFFICIENT", "押金余额不足，需授权放行");
          if (reason.isBlank()) throw new BusinessException("DEPOSIT_INSUFFICIENT", "欠费放行必须填写理由");
        }
        changeDeposit(account, amount.negate(), "EXECUTION:" + id, reason);
        db.update(
            "INSERT INTO cl_charge(id,encounter_id,source_type,source_id,amount,paid_amount,status)"
                + " VALUES (?,?,'EXECUTION',?,?,?,'CONFIRMED')",
            IdWorker.getId(),
            order.get("encounter_id"),
            id,
            amount,
            amount);
        db.update(
            "UPDATE ip_order_execution SET"
                + " status='CONFIRMED',amount=?,unit_price=?,confirmed_by=?,warning_reason=? WHERE"
                + " id=?",
            amount,
            price,
            access.current().id(),
            reason,
            id);
      }
      case "pick" -> {
        InventoryService.requireState(status, "CONFIRMED");
        db.update(
            "UPDATE ip_order_execution SET status='PICKED',verified_by=? WHERE id=?",
            access.current().id(),
            id);
      }
      case "dispense" -> {
        InventoryService.requireState(status, "PICKED");
        if (id(ex.get("verified_by")) == access.current().id())
          throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "摆药复核人与发药人须不同");
        rational.assertAllowed(
            rational.check(
                id(order.get("encounter_id")),
                List.of(
                    Map.of(
                        "item_id",
                        order.get("item_id"),
                        "dose",
                        order.get("dose"),
                        "dose_unit",
                        order.get("dose_unit"),
                        "frequency_per_day",
                        1)),
                "EXECUTION",
                id),
            text(ex, "warning_reason"));
        String control =
            db.queryForObject(
                "SELECT control_level FROM md_drug WHERE item_id=? FOR UPDATE",
                String.class,
                order.get("item_id"));
        if (!control.equals("NORMAL") && reason.isBlank())
          throw new BusinessException("CONTROL_REGISTER_REQUIRED", "请填写控制药领药人及用途");
        long doc = IdWorker.getId(), line = IdWorker.getId();
        Long unit =
            db.queryForObject(
                "SELECT base_unit_id FROM md_item WHERE id=?", Long.class, order.get("item_id"));
        db.update(
            "INSERT INTO"
                + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
                + " VALUES (?,?,'DISPENSE',?,'EXECUTED',?,?,?)",
            doc,
            "IPD" + doc,
            location,
            LocalDate.now(),
            access.current().id(),
            "住院医嘱发药");
        db.update(
            "INSERT INTO"
                + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty)"
                + " VALUES (?,?,?,?,?,1,?)",
            line,
            doc,
            order.get("item_id"),
            unit,
            order.get("base_qty"),
            order.get("base_qty"));
        var allocations =
            inventory.remove(
                location,
                id(order.get("item_id")),
                decimal(order.get("base_qty")),
                line,
                "IP-DISPENSE");
        BigDecimal cost =
            allocations.stream()
                .map(a -> decimal(a.get("cost_amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        db.update("UPDATE inv_document_item SET cost_amount=? WHERE id=?", cost, line);
        if (!control.equals("NORMAL"))
          db.update(
              "INSERT INTO"
                  + " inv_control_register(id,document_item_id,patient_id,recipient,reviewer_id,executor_id)"
                  + " VALUES (?,?,?,?,?,?)",
              IdWorker.getId(),
              line,
              db.queryForObject(
                  "SELECT patient_id FROM cl_encounter WHERE id=?",
                  Long.class,
                  order.get("encounter_id")),
              reason,
              ex.get("verified_by"),
              access.current().id());
        db.update(
            "UPDATE ip_order_execution SET"
                + " status='DISPENSED',dispensed_by=?,inventory_document_id=? WHERE id=?",
            access.current().id(),
            doc,
            id);
        accounting.post("IP-COST:" + id, "EXECUTION", id, "住院发药成本", cost, 3, 1, location);
        accounting.post(
            "IP-INCOME:" + id,
            "EXECUTION",
            id,
            "住院发药收入",
            decimal(ex.get("amount")),
            7,
            6,
            location);
      }
      case "cancel" -> {
        InventoryService.requireState(status, "PENDING", "CONFIRMED", "PICKED");
        cancelExecution(ex, order);
      }
      default -> throw new BusinessException("VALIDATION_FAILED", "操作不存在");
    }
    audit.record(action.toUpperCase(), "EXECUTION", id, reason);
  }

  private void cancelExecution(Map<String, Object> ex, Map<String, Object> order) {
    if (Set.of("CONFIRMED", "PICKED").contains(text(ex, "status"))) {
      var account =
          db.queryForMap(
              "SELECT * FROM cl_deposit_account WHERE encounter_id=? FOR UPDATE",
              order.get("encounter_id"));
      changeDeposit(
          account, decimal(ex.get("amount")), "CANCEL-EXECUTION:" + ex.get("id"), "未发医嘱取消");
      db.update(
          "UPDATE cl_charge SET status='CANCELLED' WHERE source_type='EXECUTION' AND source_id=?",
          ex.get("id"));
    }
    db.update("UPDATE ip_order_execution SET status='CANCELLED' WHERE id=?", ex.get("id"));
  }

  private void changeDeposit(Map<String, Object> a, BigDecimal delta, String event, String reason) {
    BigDecimal balance = decimal(a.get("balance")).add(delta);
    db.update(
        "UPDATE cl_deposit_account SET balance=?,charged_amount=charged_amount-? WHERE id=?",
        balance,
        delta,
        a.get("id"));
    db.update(
        "INSERT INTO"
            + " cl_deposit_ledger(id,account_id,event_key,delta,balance_after,reason,operator_id)"
            + " VALUES (?,?,?,?,?,?,?)",
        IdWorker.getId(),
        a.get("id"),
        event,
        delta,
        balance,
        reason,
        access.current().id());
  }
}
