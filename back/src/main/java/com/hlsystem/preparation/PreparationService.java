package com.hlsystem.preparation;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.common.*;
import com.hlsystem.inventory.InventoryService;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreparationService {
  private final JdbcTemplate db;
  private final AccessService access;
  private final InventoryService inventory;
  private final AccountingService accounting;
  private final AuditService audit;
  private final ObjectMapper json;

  public PreparationService(
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

  @Transactional
  public long formula(Map<String, Object> r) {
    access.require("preparation:write");
    String name = text(r, "name"), code = text(r, "code");
    if (name.isBlank() || code.isBlank())
      throw new BusinessException("VALIDATION_FAILED", "请填写配方编码和名称");
    BigDecimal output = decimal(r.get("outputQuantity"));
    positive(output, "标准产量");
    if (!(r.get("items") instanceof List<?> items) || items.isEmpty())
      throw new BusinessException("VALIDATION_FAILED", "请填写配方材料");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " prep_formula(id,code,name,output_item_id,standard_output_qty,standard_hours,standard_energy_cost,created_by)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        id,
        code,
        name,
        id(r.get("outputItemId")),
        output,
        r.get("standardHours"),
        r.get("standardEnergyCost"),
        access.current().id());
    for (Object o : items) {
      var l = (Map<?, ?>) o;
      BigDecimal qty = decimal(l.get("quantity"));
      positive(qty, "标准材料用量");
      db.update(
          "INSERT INTO prep_formula_item(id,formula_id,input_item_id,standard_base_qty) VALUES"
              + " (?,?,?,?)",
          IdWorker.getId(),
          id,
          id(l.get("itemId")),
          qty);
    }
    audit.record("CREATE", "FORMULA", id, null);
    return id;
  }

  @Transactional
  public long create(Map<String, Object> r) {
    access.require("preparation:write");
    long formula = id(r.get("formulaId")), location = id(r.get("locationId"));
    access.location(location);
    var f = db.queryForMap("SELECT * FROM prep_formula WHERE id=?", formula);
    var snapshot = new HashMap<>(f);
    snapshot.put(
        "items",
        db.queryForList(
            "SELECT * FROM prep_formula_item WHERE formula_id=? ORDER BY input_item_id", formula));
    BigDecimal qty = decimal(r.get("quantity"));
    positive(qty, "计划产量");
    if (text(r, "batchNo").isBlank()) throw new BusinessException("VALIDATION_FAILED", "请填写生产批号");
    LocalDate expiry = LocalDate.parse(text(r, "expiresOn"));
    if (!expiry.isAfter(LocalDate.now()))
      throw new BusinessException("VALIDATION_FAILED", "有效期须在未来");
    long id = IdWorker.getId();
    try {
      db.update(
          "INSERT INTO"
              + " prep_production_order(id,number,formula_id,formula_snapshot,location_id,planned_qty,batch_no,expires_on,created_by)"
              + " VALUES (?,?,?,?,?,?,?,?,?)",
          id,
          "PR" + id,
          formula,
          json.writeValueAsString(snapshot),
          location,
          qty,
          text(r, "batchNo"),
          expiry,
          access.current().id());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    audit.record("CREATE", "PRODUCTION", id, null);
    return id;
  }

  public Map<String, Object> detail(long id) {
    var p = db.queryForMap("SELECT * FROM prep_production_order WHERE id=?", id);
    access.location(id(p.get("location_id")));
    var result = new HashMap<>(p);
    result.put(
        "processes",
        db.queryForList(
            "SELECT r.*,u.display_name operator FROM prep_process_record r JOIN sys_user u ON"
                + " u.id=r.operator_id WHERE production_order_id=? ORDER BY step_no",
            id));
    result.put(
        "tests",
        db.queryForList(
            "SELECT t.*,u.display_name tester FROM prep_quality_test t JOIN sys_user u ON"
                + " u.id=t.tested_by WHERE production_order_id=? ORDER BY tested_at",
            id));
    return result;
  }

  @Transactional
  public void confirmRaw(long testId) {
    accounting.openPeriod();
    var t = db.queryForMap("SELECT * FROM prep_quality_test WHERE id=? FOR UPDATE", testId);
    InventoryService.requireState(text(t, "status"), "SUBMITTED");
    if (t.get("stock_id") == null) throw new BusinessException("VALIDATION_FAILED", "不是原料质检记录");
    if (id(t.get("tested_by")) == access.current().id())
      throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "原料检测人与放行人须不同");
    var s = db.queryForMap("SELECT * FROM inv_stock WHERE id=? FOR UPDATE", t.get("stock_id"));
    long location = id(s.get("location_id"));
    access.location(location);
    if (!text(s, "stock_status").equals("PENDING_QC"))
      throw new BusinessException("INVALID_STATE", "该库存已完成质检");
    BigDecimal qty = decimal(s.get("on_hand_qty")), cost = decimal(s.get("remaining_cost"));
    long doc = IdWorker.getId(), line = IdWorker.getId();
    var item =
        db.queryForMap(
            "SELECT i.id,i.base_unit_id FROM md_item i JOIN inv_batch b ON b.item_id=i.id JOIN"
                + " inv_cost_layer c ON c.batch_id=b.id WHERE c.id=?",
            s.get("cost_layer_id"));
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'QUALITY',?,'EXECUTED',?,?,?)",
        doc,
        "QC" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        "原料质量放行");
    db.update(
        "INSERT INTO"
            + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty,cost_amount)"
            + " VALUES (?,?,?,?,?,1,?,?)",
        line,
        doc,
        item.get("id"),
        item.get("base_unit_id"),
        qty,
        qty,
        cost);
    inventory.delta(s, qty.negate(), cost.negate(), line, "QC-OUT:" + testId);
    inventory.add(
        location,
        id(s.get("cost_layer_id")),
        text(t, "conclusion").equals("PASSED") ? "AVAILABLE" : "QUARANTINE",
        qty,
        cost,
        line,
        "QC-IN:" + testId);
    db.update(
        "UPDATE prep_quality_test SET status='APPROVED',reviewed_by=? WHERE id=?",
        access.current().id(),
        testId);
    audit.record("QUALITY_CONFIRM", "QUALITY", testId, null);
  }

  @Transactional
  public long sale(Map<String, Object> r) {
    access.require("preparation:write");
    long location = id(r.get("locationId"));
    access.location(location);
    BigDecimal qty = decimal(r.get("quantity")), price = decimal(r.get("unitPrice"));
    positive(qty, "销售数量");
    positive(price, "销售单价");
    if (text(r, "customerName").isBlank())
      throw new BusinessException("VALIDATION_FAILED", "请填写客户名称");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " prep_sales_order(id,number,location_id,item_id,customer_name,quantity,unit_price,amount,created_by)"
            + " VALUES (?,?,?,?,?,?,?,?,?)",
        id,
        "SALE" + id,
        location,
        id(r.get("itemId")),
        text(r, "customerName"),
        qty,
        price,
        money(qty.multiply(price)),
        access.current().id());
    audit.record("CREATE", "SALE", id, null);
    return id;
  }

  @Transactional
  public void executeSale(long saleId) {
    access.require("preparation:write");
    accounting.openPeriod();
    var s = db.queryForMap("SELECT * FROM prep_sales_order WHERE id=? FOR UPDATE", saleId);
    long location = id(s.get("location_id"));
    access.location(location);
    InventoryService.requireState(text(s, "status"), "DRAFT");
    if (id(s.get("created_by")) == access.current().id())
      throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "销售制单与确认人须不同");
    long doc = IdWorker.getId(), line = IdWorker.getId();
    Long unit =
        db.queryForObject(
            "SELECT base_unit_id FROM md_item WHERE id=?", Long.class, s.get("item_id"));
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'SALE',?,'EXECUTED',?,?,?)",
        doc,
        "SD" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        s.get("customer_name"));
    db.update(
        "INSERT INTO"
            + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty)"
            + " VALUES (?,?,?,?,?,1,?)",
        line,
        doc,
        s.get("item_id"),
        unit,
        s.get("quantity"),
        s.get("quantity"));
    var allocations =
        inventory.remove(location, id(s.get("item_id")), decimal(s.get("quantity")), line, "SALE");
    BigDecimal cost =
        allocations.stream()
            .map(a -> decimal(a.get("cost_amount")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    db.update("UPDATE inv_document_item SET cost_amount=? WHERE id=?", cost, line);
    db.update(
        "UPDATE prep_sales_order SET status='EXECUTED',inventory_document_id=? WHERE id=?",
        doc,
        saleId);
    accounting.post("SALE-COST:" + saleId, "SALE", saleId, "制剂销售成本", cost, 3, 1, location);
    accounting.post(
        "SALE-INCOME:" + saleId,
        "SALE",
        saleId,
        "制剂销售收款",
        decimal(s.get("amount")),
        5,
        6,
        location);
    audit.record("EXECUTE", "SALE", saleId, null);
  }

  @Transactional
  public void action(long id, String action, Map<String, Object> r) {
    access.require("preparation:write");
    accounting.openPeriod();
    var p = db.queryForMap("SELECT * FROM prep_production_order WHERE id=? FOR UPDATE", id);
    long location = id(p.get("location_id"));
    access.location(location);
    String state = text(p, "status");
    switch (action) {
      case "approve" -> {
        InventoryService.requireState(state, "DRAFT");
        if (id(p.get("created_by")) == access.current().id())
          throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "生产制单人与审核人须不同");
        db.update(
            "UPDATE prep_production_order SET status='APPROVED',approved_by=? WHERE id=?",
            access.current().id(),
            id);
      }
      case "issue" -> {
        InventoryService.requireState(state, "APPROVED");
        issue(p);
      }
      case "process" -> {
        InventoryService.requireState(state, "PRODUCING");
        String name = text(r, "name");
        if (name.isBlank()) throw new BusinessException("VALIDATION_FAILED", "请填写工序名称");
        BigDecimal hours = decimal(r.get("hours")),
            energy = decimal(r.get("energyCost")),
            output = decimal(r.get("outputQuantity"));
        if (hours.signum() < 0 || energy.signum() < 0 || output.signum() < 0)
          throw new BusinessException("VALIDATION_FAILED", "工序数量费用不能为负");
        db.update(
            "INSERT INTO"
                + " prep_process_record(id,production_order_id,step_no,name,actual_hours,output_qty,energy_cost,operator_id)"
                + " VALUES (?,?,?,?,?,?,?,?)",
            IdWorker.getId(),
            id,
            Integer.parseInt(r.get("stepNo").toString()),
            name,
            hours,
            output,
            energy,
            access.current().id());
      }
      case "test" -> {
        InventoryService.requireState(state, "PRODUCING");
        String conclusion = text(r, "conclusion"),
            standard = text(r, "standard"),
            measured = text(r, "measured");
        if (!Set.of("PASSED", "FAILED").contains(conclusion)
            || standard.isBlank()
            || measured.isBlank())
          throw new BusinessException("VALIDATION_FAILED", "请填写质检标准、实测值和结论");
        db.update(
            "INSERT INTO"
                + " prep_quality_test(id,production_order_id,type,standard_description,measured_value,conclusion,tested_by)"
                + " VALUES (?,?,'FINISHED',?,?,?,?)",
            IdWorker.getId(),
            id,
            standard,
            measured,
            conclusion,
            access.current().id());
      }
      case "settle" -> {
        InventoryService.requireState(state, "PRODUCING");
        var tests =
            db.queryForList(
                "SELECT * FROM prep_quality_test WHERE production_order_id=? ORDER BY tested_at"
                    + " DESC LIMIT 1 FOR UPDATE",
                id);
        if (tests.isEmpty() || !"PASSED".equals(tests.getFirst().get("conclusion")))
          throw new BusinessException("QUALITY_NOT_RELEASED", "成品尚未通过质检");
        if (id(tests.getFirst().get("tested_by")) == access.current().id())
          throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "质检人与成本放行人须不同");
        if (db.queryForObject(
                "SELECT COUNT(*) FROM prep_process_record WHERE production_order_id=?",
                Integer.class,
                id)
            == 0) throw new BusinessException("PROCESS_REQUIRED", "请先记录生产工序");
        settle(p, r);
        db.update(
            "UPDATE prep_quality_test SET reviewed_by=?,status='APPROVED' WHERE id=?",
            access.current().id(),
            tests.getFirst().get("id"));
      }
      default -> throw new BusinessException("VALIDATION_FAILED", "操作无效");
    }
    audit.record(action.toUpperCase(), "PRODUCTION", id, text(r, "reason"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> snapshot(Map<String, Object> p) {
    try {
      return json.readValue(p.get("formula_snapshot").toString(), Map.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private void issue(Map<String, Object> p) {
    var f = snapshot(p);
    long location = id(p.get("location_id")), doc = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'PRODUCTION_ISSUE',?,'EXECUTED',?,?,?)",
        doc,
        "MAT" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        "制剂生产领料");
    BigDecimal total = BigDecimal.ZERO,
        factor =
            decimal(p.get("planned_qty"))
                .divide(decimal(f.get("standard_output_qty")), 6, RoundingMode.HALF_UP);
    for (var line : (List<Map<String, Object>>) f.get("items")) {
      long item = id(line.get("input_item_id")), lineId = IdWorker.getId();
      int scale =
          db.queryForObject("SELECT quantity_scale FROM md_item WHERE id=?", Integer.class, item);
      BigDecimal qty =
          decimal(line.get("standard_base_qty"))
              .multiply(factor)
              .setScale(scale, RoundingMode.HALF_UP);
      long unit =
          db.queryForObject("SELECT base_unit_id FROM md_item WHERE id=?", Long.class, item);
      db.update(
          "INSERT INTO"
              + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty)"
              + " VALUES (?,?,?,?,?,1,?)",
          lineId,
          doc,
          item,
          unit,
          qty,
          qty);
      var allocations = inventory.remove(location, item, qty, lineId, "PRODUCTION-ISSUE");
      BigDecimal cost =
          allocations.stream()
              .map(a -> decimal(a.get("cost_amount")))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      total = total.add(cost);
      db.update("UPDATE inv_document_item SET cost_amount=? WHERE id=?", cost, lineId);
    }
    db.update(
        "UPDATE prep_production_order SET material_cost=?,issue_document_id=?,status='PRODUCING'"
            + " WHERE id=?",
        total,
        doc,
        p.get("id"));
    accounting.post(
        "PREP-ISSUE:" + p.get("id"),
        "PRODUCTION",
        id(p.get("id")),
        "制剂材料转在制品",
        total,
        9,
        1,
        location);
  }

  private void settle(Map<String, Object> p, Map<String, Object> r) {
    BigDecimal good = decimal(r.get("goodQuantity")),
        labor = decimal(r.get("laborCost")),
        other = decimal(r.get("otherCost"));
    positive(good, "合格产量");
    if (labor.signum() < 0 || other.signum() < 0)
      throw new BusinessException("VALIDATION_FAILED", "成本费用不能为负");
    BigDecimal energy =
        db.queryForObject(
            "SELECT COALESCE(SUM(energy_cost),0) FROM prep_process_record WHERE"
                + " production_order_id=?",
            BigDecimal.class,
            p.get("id"));
    BigDecimal extra = labor.add(energy).add(other),
        cost = decimal(p.get("material_cost")).add(extra);
    long location = id(p.get("location_id")),
        doc = IdWorker.getId(),
        line = IdWorker.getId(),
        batch = IdWorker.getId(),
        layer = IdWorker.getId();
    var f = snapshot(p);
    long item = id(f.get("output_item_id")),
        unit = db.queryForObject("SELECT base_unit_id FROM md_item WHERE id=?", Long.class, item),
        manufacturer =
            db.queryForObject(
                "SELECT COALESCE((SELECT manufacturer_id FROM md_drug WHERE item_id=?),1)",
                Long.class,
                item);
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,status,business_date,created_by,reason)"
            + " VALUES (?,?,'PRODUCTION_OUTPUT',?,'EXECUTED',?,?,?)",
        doc,
        "OUT" + doc,
        location,
        LocalDate.now(),
        access.current().id(),
        "制剂成本结算入库");
    db.update(
        "INSERT INTO"
            + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty,cost_amount,batch_no,manufactured_on,expires_on,manufacturer_id)"
            + " VALUES (?,?,?,?,?,1,?,?,?,?,?,?)",
        line,
        doc,
        item,
        unit,
        good,
        good,
        cost,
        p.get("batch_no"),
        LocalDate.now(),
        p.get("expires_on"),
        manufacturer);
    db.update(
        "INSERT INTO"
            + " inv_batch(id,item_id,manufacturer_id,batch_no,manufactured_on,expires_on,special_source)"
            + " VALUES (?,?,?,?,?,?,'PRODUCTION')",
        batch,
        item,
        manufacturer,
        p.get("batch_no"),
        LocalDate.now(),
        p.get("expires_on"));
    BigDecimal unitCost = cost.divide(good, 6, RoundingMode.HALF_UP);
    db.update(
        "INSERT INTO"
            + " inv_cost_layer(id,batch_id,receipt_item_id,base_unit_cost,original_qty,original_cost)"
            + " VALUES (?,?,?,?,?,?)",
        layer,
        batch,
        line,
        unitCost,
        good,
        cost);
    BigDecimal abnormal = money(decimal(r.get("abnormalLossCost")));
    if (abnormal.signum() < 0 || abnormal.compareTo(cost) > 0)
      throw new BusinessException("VALIDATION_FAILED", "异常损失成本超出总成本");
    if (abnormal.signum() > 0) {
      cost = cost.subtract(abnormal);
      unitCost = cost.divide(good, 6, RoundingMode.HALF_UP);
      db.update("UPDATE inv_document_item SET cost_amount=? WHERE id=?", cost, line);
      db.update(
          "UPDATE inv_cost_layer SET base_unit_cost=?,original_cost=? WHERE id=?",
          unitCost,
          cost,
          layer);
      db.update(
          "UPDATE prep_production_order SET abnormal_loss_cost=? WHERE id=?",
          abnormal,
          p.get("id"));
      accounting.post(
          "PREP-LOSS:" + p.get("id"),
          "PRODUCTION",
          id(p.get("id")),
          "制剂异常损失",
          abnormal,
          4,
          9,
          location);
    }
    inventory.add(location, layer, "AVAILABLE", good, cost, line, "PREP-OUTPUT:" + p.get("id"));
    accounting.post(
        "PREP-EXTRA:" + p.get("id"),
        "PRODUCTION",
        id(p.get("id")),
        "制剂人工能源成本",
        extra,
        9,
        5,
        location);
    accounting.post(
        "PREP-OUTPUT:" + p.get("id"),
        "PRODUCTION",
        id(p.get("id")),
        "制剂成品入库",
        cost,
        1,
        9,
        location);
    db.update(
        "UPDATE prep_production_order SET"
            + " status='COMPLETED',quality_status='PASSED',good_qty=?,labor_cost=?,energy_cost=?,other_cost=?,unit_cost=?,output_document_id=?"
            + " WHERE id=?",
        good,
        labor,
        energy,
        other,
        unitCost,
        doc,
        p.get("id"));
  }
}
