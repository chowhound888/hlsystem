package com.hlsystem.inventory;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.accounting.AccountingService;
import com.hlsystem.common.*;
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
public class InventoryService {
  private final JdbcTemplate db;
  private final AccessService access;
  private final AuditService audit;
  private final AccountingService accounting;

  public InventoryService(
      JdbcTemplate db, AccessService access, AuditService audit, AccountingService accounting) {
    this.db = db;
    this.access = access;
    this.audit = audit;
    this.accounting = accounting;
  }

  public List<Map<String, Object>> stocks(long location, String keyword) {
    access.location(location);
    String like = "%" + Objects.requireNonNullElse(keyword, "") + "%";
    return db.queryForList(
        "SELECT s.*,i.id itemId,i.code,i.name,i.specification,u.name"
            + " unit,b.batch_no,b.expires_on,b.quality_status,c.base_unit_cost,d.retail_price,(s.on_hand_qty-s.reserved_qty)"
            + " available_qty FROM inv_stock s JOIN inv_cost_layer c ON c.id=s.cost_layer_id JOIN"
            + " inv_batch b ON b.id=c.batch_id JOIN md_item i ON i.id=b.item_id JOIN md_unit u ON"
            + " u.id=i.base_unit_id LEFT JOIN md_drug d ON d.item_id=i.id WHERE s.location_id=? AND"
            + " (i.name LIKE ? OR i.code LIKE ?) AND s.on_hand_qty>0 ORDER BY"
            + " i.code,b.expires_on,c.id",
        location,
        like,
        like);
  }

  public List<Map<String, Object>> documents(long location) {
    access.location(location);
    return db.queryForList(
        "SELECT d.*,u.display_name creator,l.name location,t.name target FROM inv_document d JOIN"
            + " sys_user u ON u.id=d.created_by JOIN sys_location l ON l.id=d.location_id LEFT JOIN"
            + " sys_location t ON t.id=d.target_location_id WHERE d.location_id=? OR"
            + " d.target_location_id=? ORDER BY d.created_at DESC LIMIT 200",
        location,
        location);
  }

  public Map<String, Object> detail(long documentId) {
    var d = doc(documentId, false);
    checkVisible(d);
    var result = new HashMap<>(d);
    result.put(
        "items",
        db.queryForList(
            "SELECT x.*,COALESCE(x.name_snapshot,i.name)"
                + " name,COALESCE(x.specification_snapshot,i.specification)"
                + " specification,COALESCE(x.unit_snapshot,u.name) unit FROM inv_document_item x"
                + " JOIN md_item i ON i.id=x.item_id JOIN md_unit u ON u.id=x.unit_id WHERE"
                + " x.document_id=? ORDER BY x.id",
            documentId));
    result.put(
        "actions",
        db.queryForList(
            "SELECT a.*,u.display_name operator FROM sys_document_action a JOIN sys_user u ON"
                + " u.id=a.operator_id WHERE a.source_type='INVENTORY' AND a.source_id=? ORDER BY"
                + " a.occurred_at",
            documentId));
    return result;
  }

  @Transactional
  public long create(Map<String, Object> request) {
    access.require("inventory:write");
    String type = text(request, "type");
    if (!Set.of("RECEIPT", "TRANSFER", "LOSS", "ISSUE", "SUPPLIER_RETURN").contains(type))
      throw new BusinessException("VALIDATION_FAILED", "单据类型不正确");
    long location = id(request.get("locationId"));
    access.location(location);
    Long target =
        request.get("targetLocationId") == null ? null : id(request.get("targetLocationId"));
    if (type.equals("TRANSFER")
        && (target == null
            || target == location
            || db.queryForObject(
                    "SELECT COUNT(*) FROM sys_location WHERE id=? AND enabled=TRUE AND"
                        + " type<>'TRANSIT'",
                    Integer.class,
                    target)
                == 0)) throw new BusinessException("VALIDATION_FAILED", "请选择不同的有效接收机构");
    Long supplier = request.get("supplierId") == null ? null : id(request.get("supplierId"));
    if (type.equals("SUPPLIER_RETURN") && supplier == null)
      throw new BusinessException("VALIDATION_FAILED", "退供必须选择原供应商");
    String reason = text(request, "reason");
    if (Set.of("LOSS", "ISSUE", "SUPPLIER_RETURN").contains(type) && reason.isBlank())
      throw new BusinessException("VALIDATION_FAILED", "请填写出库原因");
    String source = text(request, "specialSource");
    if (source.isBlank()) source = "PURCHASE";
    if (!Set.of("PURCHASE", "GIFT", "EXPERIMENT").contains(source))
      throw new BusinessException("VALIDATION_FAILED", "药品来源无效");
    if (type.equals("RECEIPT") && source.equals("PURCHASE") && supplier == null)
      throw new BusinessException("VALIDATION_FAILED", "采购入库必须选择供应商");
    if (!(request.get("items") instanceof List<?> list) || list.isEmpty() || list.size() > 200)
      throw new BusinessException("VALIDATION_FAILED", "请填写1至200行药品明细");
    long documentId = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_document(id,number,type,location_id,target_location_id,supplier_id,business_date,created_by,reason,special_source)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)",
        documentId,
        "IN" + documentId,
        type,
        location,
        target,
        supplier,
        LocalDate.now(),
        access.current().id(),
        reason,
        source);
    for (Object value : list) {
      if (!(value instanceof Map<?, ?> r))
        throw new BusinessException("VALIDATION_FAILED", "明细格式不正确");
      long itemId = id(r.get("itemId")), unitId = id(r.get("unitId"));
      BigDecimal qty = decimal(r.get("quantity"));
      positive(qty, "数量");
      var units =
          db.queryForList(
              "SELECT iu.factor_to_base,i.quantity_scale,d.manufacturer_id FROM md_item_unit iu"
                  + " JOIN md_item i ON i.id=iu.item_id LEFT JOIN md_drug d ON d.item_id=i.id WHERE"
                  + " iu.item_id=? AND iu.unit_id=? AND i.enabled=TRUE",
              itemId,
              unitId);
      if (units.isEmpty()) throw new BusinessException("VALIDATION_FAILED", "药品已停用或单位不属于药品");
      var unit = units.getFirst();
      BigDecimal factor = decimal(unit.get("factor_to_base")), base = qty.multiply(factor);
      if (base.stripTrailingZeros().scale() > ((Number) unit.get("quantity_scale")).intValue())
        throw new BusinessException("VALIDATION_FAILED", "发药数量不符合基础单位精度");
      String batchNo = r.get("batchNo") == null ? null : r.get("batchNo").toString();
      LocalDate made = null, expires = null;
      BigDecimal cost = decimal(r.get("unitCost"));
      if (cost.signum() < 0) throw new BusinessException("VALIDATION_FAILED", "采购单价不能为负");
      if (type.equals("RECEIPT")) {
        if (batchNo == null
            || batchNo.isBlank()
            || r.get("manufacturedOn") == null
            || r.get("expiresOn") == null)
          throw new BusinessException("VALIDATION_FAILED", "入库必须填写批号、生产日期和有效期");
        made = LocalDate.parse(r.get("manufacturedOn").toString());
        expires = LocalDate.parse(r.get("expiresOn").toString());
        if (made.isAfter(LocalDate.now()) || expires.isBefore(made))
          throw new BusinessException("VALIDATION_FAILED", "批次日期不正确");
        if (source.equals("GIFT")) cost = BigDecimal.ZERO;
      }
      Long manufacturer =
          unit.get("manufacturer_id") == null
              ? (r.get("manufacturerId") == null ? null : id(r.get("manufacturerId")))
              : id(unit.get("manufacturer_id"));
      if (type.equals("RECEIPT") && manufacturer == null)
        throw new BusinessException("VALIDATION_FAILED", "请填写物料厂家");
      db.update(
          "INSERT INTO"
              + " inv_document_item(id,document_id,item_id,unit_id,quantity,factor_snapshot,base_qty,batch_no,manufactured_on,expires_on,manufacturer_id,unit_cost,cost_amount)"
              + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
          IdWorker.getId(),
          documentId,
          itemId,
          unitId,
          qty,
          factor,
          base,
          batchNo,
          made,
          expires,
          manufacturer,
          cost,
          money(qty.multiply(cost)));
    }
    db.update(
        "UPDATE inv_document_item x JOIN md_item i ON i.id=x.item_id JOIN md_unit u ON"
            + " u.id=x.unit_id SET"
            + " x.name_snapshot=i.name,x.specification_snapshot=i.specification,x.unit_snapshot=u.name"
            + " WHERE x.document_id=?",
        documentId);
    audit.transition("INVENTORY", documentId, null, "DRAFT", "CREATE", reason);
    return documentId;
  }

  @Transactional
  public void action(long documentId, String action, int version, String reason) {
    if (action.equals("execute") || action.equals("receive")) accounting.openPeriod();
    var d = doc(documentId, true);
    String from = text(d, "status"), to;
    if (action.equals("receive")) {
      access.require("inventory:execute");
      if (d.get("target_location_id") == null)
        throw new BusinessException("INVALID_STATE", "单据不是调拨单");
      access.location(id(d.get("target_location_id")));
    } else access.location(id(d.get("location_id")));
    if (((Number) d.get("version")).intValue() != version)
      throw new BusinessException("VERSION_CONFLICT", "单据已经变化，请刷新", HttpStatus.CONFLICT);
    switch (action) {
      case "submit" -> {
        access.require("inventory:write");
        requireState(from, "DRAFT", "REJECTED");
        to = "SUBMITTED";
      }
      case "approve" -> {
        access.require("inventory:approve");
        requireState(from, "SUBMITTED");
        if (id(d.get("created_by")) == access.current().id())
          throw new BusinessException("INDEPENDENT_REVIEW_REQUIRED", "制单人与审核人必须不同");
        to = "APPROVED";
        db.update(
            "UPDATE inv_document SET approved_by=? WHERE id=?", access.current().id(), documentId);
      }
      case "reject" -> {
        access.require("inventory:approve");
        requireState(from, "SUBMITTED");
        if (reason == null || reason.isBlank())
          throw new BusinessException("VALIDATION_FAILED", "请填写驳回原因");
        to = "REJECTED";
      }
      case "cancel" -> {
        access.require("inventory:write");
        requireState(from, "DRAFT", "SUBMITTED", "REJECTED", "APPROVED");
        to = "CANCELLED";
      }
      case "execute" -> {
        access.require("inventory:execute");
        requireState(from, "APPROVED");
        execute(d);
        to = text(d, "type").equals("TRANSFER") ? "SHIPPED" : "EXECUTED";
      }
      case "receive" -> {
        requireState(from, "SHIPPED");
        receive(d);
        to = "RECEIVED";
      }
      default -> throw new BusinessException("VALIDATION_FAILED", "操作不存在");
    }
    db.update("UPDATE inv_document SET status=?,version=version+1 WHERE id=?", to, documentId);
    audit.transition("INVENTORY", documentId, from, to, action.toUpperCase(), reason);
  }

  private void execute(Map<String, Object> d) {
    long documentId = id(d.get("id")), location = id(d.get("location_id"));
    String type = text(d, "type");
    BigDecimal total = BigDecimal.ZERO;
    var lines =
        db.queryForList(
            "SELECT * FROM inv_document_item WHERE document_id=? ORDER BY item_id,id", documentId);
    for (var line : lines) {
      long itemId = id(line.get("item_id"));
      db.queryForList("SELECT id FROM md_drug WHERE item_id=? FOR UPDATE", itemId);
      checkStocktake(location);
      BigDecimal cost;
      if (type.equals("RECEIPT")) cost = receipt(d, line);
      else {
        var allocations =
            remove(location, itemId, decimal(line.get("base_qty")), id(line.get("id")), type);
        cost =
            allocations.stream()
                .map(a -> decimal(a.get("cost_amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (type.equals("TRANSFER"))
          for (var a : allocations)
            add(
                9L,
                id(a.get("cost_layer_id")),
                "AVAILABLE",
                decimal(a.get("base_qty")),
                decimal(a.get("cost_amount")),
                id(line.get("id")),
                "TRANSIT:" + a.get("id"));
      }
      db.update("UPDATE inv_document_item SET cost_amount=? WHERE id=?", cost, line.get("id"));
      total = total.add(cost);
    }
    if (type.equals("RECEIPT")) {
      accounting.post("INV:" + documentId, "INVENTORY", documentId, "采购入库", total, 1, 2, location);
      if (d.get("supplier_id") != null && total.signum() > 0)
        db.update(
            "INSERT INTO acc_payable(id,supplier_id,document_id,amount) VALUES (?,?,?,?)",
            IdWorker.getId(),
            d.get("supplier_id"),
            documentId,
            total);
    } else if (type.equals("LOSS"))
      accounting.post("INV:" + documentId, "INVENTORY", documentId, "药品报损", total, 4, 1, location);
    else if (type.equals("ISSUE"))
      accounting.post("INV:" + documentId, "INVENTORY", documentId, "科室领用", total, 3, 1, location);
    else if (type.equals("SUPPLIER_RETURN")) {
      for (var line : lines)
        for (var allocation :
            db.queryForList(
                "SELECT a.*,ri.document_id receiptDocument FROM inv_allocation a JOIN"
                    + " inv_cost_layer c ON c.id=a.cost_layer_id JOIN inv_document_item ri ON"
                    + " ri.id=c.receipt_item_id WHERE a.document_item_id=?",
                line.get("id"))) {
          var payable =
              db.queryForMap(
                  "SELECT p.* FROM acc_payable p WHERE p.document_id=? AND supplier_id=? FOR"
                      + " UPDATE",
                  allocation.get("receiptDocument"),
                  d.get("supplier_id"));
          db.update(
              "UPDATE acc_payable SET amount=amount-? WHERE id=?",
              allocation.get("cost_amount"),
              payable.get("id"));
        }
      accounting.post("INV:" + documentId, "INVENTORY", documentId, "退供出库", total, 2, 1, location);
    }
    db.update(
        "UPDATE inv_document SET executed_by=?,executed_at=CURRENT_TIMESTAMP(3) WHERE id=?",
        access.current().id(),
        documentId);
  }

  private BigDecimal receipt(Map<String, Object> d, Map<String, Object> line) {
    var batches =
        db.queryForList(
            "SELECT * FROM inv_batch WHERE item_id=? AND manufacturer_id=? AND batch_no=? FOR"
                + " UPDATE",
            line.get("item_id"),
            line.get("manufacturer_id"),
            line.get("batch_no"));
    long batchId;
    if (batches.isEmpty()) {
      batchId = IdWorker.getId();
      db.update(
          "INSERT INTO"
              + " inv_batch(id,item_id,manufacturer_id,batch_no,manufactured_on,expires_on,special_source)"
              + " VALUES (?,?,?,?,?,?,?)",
          batchId,
          line.get("item_id"),
          line.get("manufacturer_id"),
          line.get("batch_no"),
          line.get("manufactured_on"),
          line.get("expires_on"),
          d.get("special_source"));
    } else {
      var b = batches.getFirst();
      batchId = id(b.get("id"));
      if (!b.get("expires_on").toString().equals(line.get("expires_on").toString())
          || !b.get("manufactured_on").toString().equals(line.get("manufactured_on").toString()))
        throw new BusinessException("BATCH_CONFLICT", "同一厂家批号的日期不一致");
    }
    BigDecimal qty = decimal(line.get("base_qty")), cost = decimal(line.get("cost_amount"));
    long layer = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_cost_layer(id,batch_id,receipt_item_id,base_unit_cost,original_qty,original_cost)"
            + " VALUES (?,?,?,?,?,?)",
        layer,
        batchId,
        line.get("id"),
        cost.divide(qty, 6, RoundingMode.HALF_UP),
        qty,
        cost);
    String itemType =
        db.queryForObject(
            "SELECT item_type FROM md_item WHERE id=?", String.class, line.get("item_id"));
    add(
        id(d.get("location_id")),
        layer,
        itemType.equals("DRUG") ? "AVAILABLE" : "PENDING_QC",
        qty,
        cost,
        id(line.get("id")),
        "RECEIPT:" + line.get("id"));
    return cost;
  }

  private void receive(Map<String, Object> d) {
    long documentId = id(d.get("id")), target = id(d.get("target_location_id"));
    checkStocktake(target);
    var allocations =
        db.queryForList(
            "SELECT a.* FROM inv_allocation a JOIN inv_document_item x ON x.id=a.document_item_id"
                + " WHERE x.document_id=? ORDER BY a.cost_layer_id,a.id FOR UPDATE",
            documentId);
    for (var a : allocations) {
      long layer = id(a.get("cost_layer_id"));
      BigDecimal qty = decimal(a.get("base_qty")), cost = decimal(a.get("cost_amount"));
      var transit =
          db.queryForMap(
              "SELECT * FROM inv_stock WHERE location_id=9 AND cost_layer_id=? AND"
                  + " stock_status='AVAILABLE' FOR UPDATE",
              layer);
      delta(
          transit,
          qty.negate(),
          cost.negate(),
          id(a.get("document_item_id")),
          "RECEIVE-OUT:" + a.get("id"));
      add(
          target,
          layer,
          "AVAILABLE",
          qty,
          cost,
          id(a.get("document_item_id")),
          "RECEIVE-IN:" + a.get("id"));
      db.update(
          "UPDATE inv_allocation SET received_qty=base_qty,received_cost=cost_amount WHERE id=?",
          a.get("id"));
    }
  }

  public List<Map<String, Object>> remove(
      long location, long itemId, BigDecimal quantity, long documentItem, String event) {
    checkStocktake(location);
    positive(quantity, "出库数量");
    boolean disposal = Set.of("LOSS", "SUPPLIER_RETURN").contains(event);
    String restrictions =
        disposal
            ? ""
            : " AND s.stock_status='AVAILABLE' AND b.quality_status='PASSED' AND"
                + " b.expires_on>=CURDATE() AND i.enabled=TRUE AND"
                + " b.special_source<>'EXPERIMENT'";
    if (event.equals("SUPPLIER_RETURN")) {
      Long supplier =
          db.queryForObject(
              "SELECT d.supplier_id FROM inv_document d JOIN inv_document_item x ON"
                  + " x.document_id=d.id WHERE x.id=?",
              Long.class,
              documentItem);
      restrictions +=
          " AND c.receipt_item_id IN (SELECT ri.id FROM inv_document_item ri JOIN inv_document rd"
              + " ON rd.id=ri.document_id WHERE rd.supplier_id="
              + supplier
              + ")";
    }
    var candidates =
        db.queryForList(
            "SELECT s.*,b.expires_on,b.quality_status FROM inv_stock s JOIN inv_cost_layer c ON"
                + " c.id=s.cost_layer_id JOIN inv_batch b ON b.id=c.batch_id JOIN md_item i ON"
                + " i.id=b.item_id WHERE s.location_id=? AND b.item_id=? AND s.frozen=FALSE"
                + restrictions
                + " AND s.on_hand_qty>s.reserved_qty ORDER BY b.expires_on,c.created_at,c.id FOR"
                + " UPDATE",
            location,
            itemId);
    BigDecimal available =
        candidates.stream()
            .map(s -> decimal(s.get("on_hand_qty")).subtract(decimal(s.get("reserved_qty"))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (available.compareTo(quantity) < 0)
      throw new BusinessException("STOCK_INSUFFICIENT", "药品可用库存不足（品种编号 " + itemId + "）");
    var allocations = new ArrayList<Map<String, Object>>();
    BigDecimal remaining = quantity;
    for (var s : candidates) {
      if (remaining.signum() == 0) break;
      BigDecimal onHand = decimal(s.get("on_hand_qty")),
          qty = remaining.min(onHand.subtract(decimal(s.get("reserved_qty"))));
      BigDecimal cost =
          qty.compareTo(onHand) == 0
              ? decimal(s.get("remaining_cost"))
              : money(
                  decimal(s.get("remaining_cost"))
                      .multiply(qty)
                      .divide(onHand, 8, RoundingMode.HALF_UP));
      long allocation = IdWorker.getId();
      delta(s, qty.negate(), cost.negate(), documentItem, event + ":" + allocation);
      db.update(
          "INSERT INTO"
              + " inv_allocation(id,document_item_id,source_stock_id,cost_layer_id,base_qty,cost_amount)"
              + " VALUES (?,?,?,?,?,?)",
          allocation,
          documentItem,
          s.get("id"),
          s.get("cost_layer_id"),
          qty,
          cost);
      allocations.add(
          Map.of(
              "id",
              allocation,
              "cost_layer_id",
              s.get("cost_layer_id"),
              "base_qty",
              qty,
              "cost_amount",
              cost));
      remaining = remaining.subtract(qty);
    }
    return allocations;
  }

  public long add(
      long location,
      long layer,
      String status,
      BigDecimal qty,
      BigDecimal cost,
      long line,
      String event) {
    checkStocktake(location);
    db.update(
        "INSERT IGNORE INTO inv_stock(id,location_id,cost_layer_id,stock_status) VALUES (?,?,?,?)",
        IdWorker.getId(),
        location,
        layer,
        status);
    var stock =
        db.queryForMap(
            "SELECT * FROM inv_stock WHERE location_id=? AND cost_layer_id=? AND stock_status=? FOR"
                + " UPDATE",
            location,
            layer,
            status);
    delta(stock, qty, cost, line, event);
    return id(stock.get("id"));
  }

  public void delta(
      Map<String, Object> stock, BigDecimal qty, BigDecimal cost, long line, String event) {
    db.update(
        "UPDATE inv_document_item x JOIN md_item i ON i.id=x.item_id JOIN md_unit u ON"
            + " u.id=x.unit_id SET"
            + " x.name_snapshot=i.name,x.specification_snapshot=i.specification,x.unit_snapshot=u.name"
            + " WHERE x.id=? AND x.name_snapshot IS NULL",
        line);
    BigDecimal balance = decimal(stock.get("on_hand_qty")).add(qty),
        balanceCost = decimal(stock.get("remaining_cost")).add(cost);
    if (balance.signum() < 0 || balanceCost.signum() < 0)
      throw new BusinessException("STOCK_INSUFFICIENT", "库存余额不足");
    db.update(
        "UPDATE inv_stock SET on_hand_qty=?,remaining_cost=?,version=version+1 WHERE id=?",
        balance,
        balanceCost,
        stock.get("id"));
    db.update(
        "INSERT INTO"
            + " inv_ledger(id,stock_id,document_item_id,event_key,business_date,qty_delta,cost_delta,balance_qty,balance_cost,operator_id)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)",
        IdWorker.getId(),
        stock.get("id"),
        line,
        event,
        LocalDate.now(),
        qty,
        cost,
        balance,
        balanceCost,
        access.current().id());
  }

  public void checkStocktake(long location) {
    db.queryForMap("SELECT id FROM sys_location WHERE id=? FOR UPDATE", location);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM inv_closing WHERE location_id=? AND status='CLOSED' AND"
                + " period_start<=? AND period_end>=?",
            Integer.class,
            location,
            LocalDate.now(),
            LocalDate.now())
        > 0) throw new BusinessException("PERIOD_CLOSED", "该机构当前日期已结账");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM inv_stocktake WHERE location_id=? AND status IN"
                + " ('COUNTING','SUBMITTED')",
            Integer.class,
            location)
        > 0) throw new BusinessException("STOCK_FROZEN", "该机构正在盘点，库存操作已冻结");
  }

  public Map<String, Object> doc(long id, boolean lock) {
    var docs =
        db.queryForList("SELECT * FROM inv_document WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (docs.isEmpty()) throw new BusinessException("NOT_FOUND", "单据不存在", HttpStatus.NOT_FOUND);
    return docs.getFirst();
  }

  private void checkVisible(Map<String, Object> d) {
    if (!access.current().locationIds().contains(id(d.get("location_id")))
        && (d.get("target_location_id") == null
            || !access.current().locationIds().contains(id(d.get("target_location_id")))))
      throw new BusinessException("NOT_FOUND", "单据不存在", HttpStatus.NOT_FOUND);
  }

  public static void requireState(String state, String... allowed) {
    if (!List.of(allowed).contains(state))
      throw new BusinessException("INVALID_STATE", "当前状态不允许此操作，请刷新", HttpStatus.CONFLICT);
  }
}
