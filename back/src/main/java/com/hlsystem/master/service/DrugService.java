package com.hlsystem.master.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.AuditService;
import com.hlsystem.common.BusinessException;
import com.hlsystem.master.dto.DrugRequest;
import com.hlsystem.master.entity.Drug;
import com.hlsystem.master.entity.Item;
import com.hlsystem.master.mapper.DrugMapper;
import com.hlsystem.master.mapper.ItemMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DrugService {
  private final JdbcTemplate db;
  private final ItemMapper items;
  private final DrugMapper drugs;
  private final AuditService audit;
  private static final String SELECT =
      "SELECT i.*,d.id drugId,d.generic_name genericName,d.trade_name tradeName,d.manufacturer_id"
          + " manufacturerId,d.dosage_form dosageForm,d.category,d.insurance_code"
          + " insuranceCode,d.insurance_category insuranceCategory,d.control_level"
          + " controlLevel,d.prescription_required prescriptionRequired,d.imported,d.in_house"
          + " inHouse,d.self_pay selfPay,d.valuable,d.retail_price retailPrice,m.name"
          + " manufacturer,u.name baseUnit FROM md_item i JOIN md_drug d ON d.item_id=i.id JOIN"
          + " md_manufacturer m ON m.id=d.manufacturer_id JOIN md_unit u ON u.id=i.base_unit_id";

  public DrugService(JdbcTemplate db, ItemMapper items, DrugMapper drugs, AuditService audit) {
    this.db = db;
    this.items = items;
    this.drugs = drugs;
    this.audit = audit;
  }

  public Map<String, Object> list(String keyword, int page, int pageSize) {
    page = Math.max(1, page);
    pageSize = Math.clamp(pageSize, 1, 100);
    String filter =
        " WHERE (i.name LIKE ? OR i.code LIKE ? OR d.trade_name LIKE ? OR EXISTS (SELECT 1 FROM"
            + " md_drug_alias a WHERE a.drug_id=d.id AND a.alias LIKE ?))";
    String like = "%" + Objects.requireNonNullElse(keyword, "") + "%";
    var records =
        db.queryForList(
            SELECT + filter + " ORDER BY i.code LIMIT ? OFFSET ?",
            like,
            like,
            like,
            like,
            pageSize,
            (page - 1) * pageSize);
    Long total =
        db.queryForObject(
            "SELECT COUNT(*) FROM md_item i JOIN md_drug d ON d.item_id=i.id" + filter,
            Long.class,
            like,
            like,
            like,
            like);
    return Map.of("records", records, "total", total, "page", page, "pageSize", pageSize);
  }

  public Map<String, Object> detail(long id) {
    var rows = db.queryForList(SELECT + " WHERE i.id=?", id);
    if (rows.isEmpty()) throw new BusinessException("NOT_FOUND", "药品不存在", HttpStatus.NOT_FOUND);
    var result = new HashMap<>(rows.getFirst());
    result.put(
        "aliases",
        db.queryForList(
            "SELECT alias FROM md_drug_alias WHERE drug_id=?", String.class, result.get("drugId")));
    result.put(
        "units",
        db.queryForList(
            "SELECT iu.id,iu.unit_id unitId,iu.factor_to_base factor,u.name FROM md_item_unit iu"
                + " JOIN md_unit u ON u.id=iu.unit_id WHERE iu.item_id=?",
            id));
    return result;
  }

  @Transactional
  public long create(DrugRequest r) {
    validate(r);
    var item = new Item();
    item.setId(IdWorker.getId());
    item.setCode(r.code().trim());
    item.setName(r.name());
    item.setSpecification(r.specification());
    item.setItemType("DRUG");
    item.setBaseUnitId(r.baseUnitId());
    item.setQuantityScale(r.quantityScale());
    item.setEnabled(true);
    item.setVersion(0);
    items.insert(item);
    var drug = new Drug();
    drug.setId(IdWorker.getId());
    drug.setItemId(item.getId());
    apply(drug, r);
    drug.setPriceVersion(0);
    drugs.insert(drug);
    db.update(
        "INSERT INTO md_item_unit(id,item_id,unit_id,factor_to_base) VALUES (?,?,?,1)",
        IdWorker.getId(),
        item.getId(),
        r.baseUnitId());
    if (r.packagingUnitId() != null && !r.packagingUnitId().equals(r.baseUnitId()))
      db.update(
          "INSERT INTO md_item_unit(id,item_id,unit_id,factor_to_base) VALUES (?,?,?,?)",
          IdWorker.getId(),
          item.getId(),
          r.packagingUnitId(),
          r.packagingFactor());
    saveAliases(drug.getId(), r.aliases());
    audit.record("CREATE", "DRUG", item.getId(), null);
    return item.getId();
  }

  @Transactional
  public void update(long id, DrugRequest r) {
    validate(r);
    var item = items.selectById(id);
    if (item == null) throw new BusinessException("NOT_FOUND", "药品不存在", HttpStatus.NOT_FOUND);
    var current = db.queryForMap("SELECT * FROM md_drug WHERE item_id=? FOR UPDATE", id);
    if (!Objects.equals(item.getVersion(), r.version()))
      throw new BusinessException("VERSION_CONFLICT", "药品已被其他人修改，请刷新", HttpStatus.CONFLICT);
    if (!item.getBaseUnitId().equals(r.baseUnitId()))
      throw new BusinessException("UNIT_IMMUTABLE", "已有药品不能变更基础单位，请建立新规格");
    if (new java.math.BigDecimal(current.get("retail_price").toString()).compareTo(r.retailPrice())
        != 0) throw new BusinessException("PRICE_ADJUSTMENT_REQUIRED", "价格修改请使用调价单");
    int changed =
        db.update(
            "UPDATE md_item SET code=?,name=?,specification=?,quantity_scale=?,version=version+1"
                + " WHERE id=? AND version=?",
            r.code().trim(),
            r.name(),
            r.specification(),
            r.quantityScale(),
            id,
            r.version());
    if (changed != 1)
      throw new BusinessException("VERSION_CONFLICT", "数据已变化，请刷新", HttpStatus.CONFLICT);
    var drug = drugs.selectById(((Number) current.get("id")).longValue());
    apply(drug, r);
    drugs.updateById(drug);
    saveAliases(drug.getId(), r.aliases());
    audit.record("UPDATE", "DRUG", id, null);
  }

  @Transactional
  public void enabled(long id, boolean value) {
    if (db.update("UPDATE md_item SET enabled=?,version=version+1 WHERE id=?", value, id) != 1)
      throw new BusinessException("NOT_FOUND", "药品不存在", HttpStatus.NOT_FOUND);
    audit.record(value ? "ENABLE" : "DISABLE", "DRUG", id, null);
  }

  private void validate(DrugRequest r) {
    if (!Set.of("NORMAL", "TOXIC", "NARCOTIC", "PSYCHOTROPIC").contains(r.controlLevel()))
      throw new BusinessException("VALIDATION_FAILED", "药品控制级别不正确");
    if (r.packagingUnitId() != null && r.packagingFactor() == null)
      throw new BusinessException("VALIDATION_FAILED", "请填写包装换算系数");
  }

  private void apply(Drug d, DrugRequest r) {
    d.setGenericName(r.name());
    d.setTradeName(r.tradeName());
    d.setManufacturerId(r.manufacturerId());
    d.setDosageForm(r.dosageForm());
    d.setCategory(r.category());
    d.setInsuranceCode(r.insuranceCode());
    d.setInsuranceCategory(r.insuranceCategory());
    d.setPrescriptionRequired(r.prescriptionRequired());
    d.setControlLevel(r.controlLevel());
    d.setImported(r.imported());
    d.setInHouse(r.inHouse());
    d.setSelfPay(r.selfPay());
    d.setValuable(r.valuable());
    d.setRetailPrice(r.retailPrice());
  }

  private void saveAliases(long drugId, List<String> aliases) {
    db.update("DELETE FROM md_drug_alias WHERE drug_id=?", drugId);
    if (aliases != null)
      aliases.stream()
          .map(String::trim)
          .distinct()
          .forEach(
              a ->
                  db.update(
                      "INSERT INTO md_drug_alias VALUES (?,?,?)", IdWorker.getId(), drugId, a));
  }
}
