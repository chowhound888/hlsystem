package com.hlsystem.rational;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hlsystem.common.BusinessException;
import com.hlsystem.security.AccessService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RationalService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final AccessService access;

  public RationalService(JdbcTemplate db, ObjectMapper json, AccessService access) {
    this.db = db;
    this.json = json;
    this.access = access;
  }

  public Map<String, Object> check(
      long encounter, List<Map<String, Object>> lines, String type, long source) {
    var patient =
        db.queryForMap(
            "SELECT p.* FROM cl_patient p JOIN cl_encounter e ON e.patient_id=p.id WHERE e.id=?",
            encounter);
    Set<Long> itemIds = new HashSet<>();
    for (var line : lines) itemIds.add(id(line.get("item_id")));
    Set<Long> diagnoses =
        new HashSet<>(
            db.queryForList(
                "SELECT diagnosis_id FROM cl_encounter_diagnosis WHERE encounter_id=?",
                Long.class,
                encounter));
    var results = new ArrayList<Map<String, Object>>();
    int age =
        java.time.Period.between(
                java.time.LocalDate.parse(patient.get("birth_date").toString()),
                java.time.LocalDate.now())
            .getYears();
    if (text(patient, "allergy_notes").isBlank())
      results.add(result("ALLERGY", "INCOMPLETE", "患者过敏史尚未确认，请先补充资料", patient.get("id")));
    for (var allergy :
        db.queryForList("SELECT * FROM cl_patient_allergy WHERE patient_id=?", patient.get("id"))) {
      boolean matched =
          allergy.get("item_id") != null && itemIds.contains(id(allergy.get("item_id")));
      if (allergy.get("ingredient_id") != null)
        for (long item : itemIds)
          if (db.queryForObject(
                  "SELECT COUNT(*) FROM md_drug_ingredient di JOIN md_drug d ON d.id=di.drug_id"
                      + " WHERE d.item_id=? AND di.ingredient_id=?",
                  Integer.class,
                  item,
                  allergy.get("ingredient_id"))
              > 0) matched = true;
      if (matched)
        results.add(
            result(
                "ALLERGY", "BLOCK", "患者药物过敏史命中：" + text(allergy, "reaction"), allergy.get("id")));
    }
    Set<Long> seenIngredients = new HashSet<>();
    Set<Long> seenItems = new HashSet<>();
    for (var line : lines) {
      long item = id(line.get("item_id"));
      if (!seenItems.add(item)) results.add(result("DUPLICATE", "WARNING", "处方包含重复药品", item));
      for (long ingredient :
          db.queryForList(
              "SELECT di.ingredient_id FROM md_drug_ingredient di JOIN md_drug d ON d.id=di.drug_id"
                  + " WHERE d.item_id=?",
              Long.class,
              item))
        if (!seenIngredients.add(ingredient))
          results.add(result("DUPLICATE", "WARNING", "多个药品含相同成分，需检查叠加剂量", ingredient));
    }
    for (var rule : db.queryForList("SELECT * FROM ru_rule WHERE enabled=TRUE ORDER BY id")) {
      long item = id(rule.get("item_id"));
      if (!itemIds.contains(item)
          && !(text(rule, "type").equals("INTERACTION")
              && rule.get("peer_item_id") != null
              && itemIds.contains(id(rule.get("peer_item_id"))))) continue;
      if (rule.get("min_age") != null && age < ((Number) rule.get("min_age")).intValue()) continue;
      if (rule.get("max_age") != null && age > ((Number) rule.get("max_age")).intValue()) continue;
      String ruleType = text(rule, "type");
      boolean hit = false;
      switch (ruleType) {
        case "INTERACTION" ->
            hit =
                rule.get("peer_item_id") != null
                    && itemIds.contains(item)
                    && itemIds.contains(id(rule.get("peer_item_id")));
        case "CONTRAINDICATION" ->
            hit =
                rule.get("diagnosis_id") != null
                    && diagnoses.contains(id(rule.get("diagnosis_id")));
        case "INDICATION" ->
            hit =
                rule.get("diagnosis_id") != null
                    && !diagnoses.contains(id(rule.get("diagnosis_id")));
        case "DOSE" -> {
          for (var line : lines)
            if (id(line.get("item_id")) == item) {
              if (rule.get("route") != null
                  && !text(rule, "route").isBlank()
                  && !text(rule, "route").equals(text(line, "route"))) continue;
              BigDecimal dose = decimal(line.get("dose"));
              String from = text(line, "dose_unit"), to = text(rule, "dose_unit");
              if (!from.equals(to)) {
                if (from.equals("g") && to.equals("mg"))
                  dose = dose.multiply(new BigDecimal("1000"));
                else if (from.equals("mg") && to.equals("g"))
                  dose = dose.divide(new BigDecimal("1000"));
                else {
                  results.add(result("DOSE", "INCOMPLETE", "剂量单位不可换算，需核实后审查", rule.get("id")));
                  continue;
                }
              }
              if (!text(rule, "dose_basis").equals("SINGLE"))
                dose = dose.multiply(decimal(line.get("frequency_per_day")));
              if (Boolean.TRUE.equals(rule.get("per_kg"))) {
                BigDecimal weight = decimal(patient.get("weight_kg"));
                if (weight.signum() <= 0) {
                  results.add(result("DOSE", "INCOMPLETE", "体重缺失，无法完成按体重剂量审查", rule.get("id")));
                  continue;
                }
                dose = dose.divide(weight, 6, java.math.RoundingMode.HALF_UP);
              }
              if ((rule.get("min_dose") != null
                      && dose.compareTo(decimal(rule.get("min_dose"))) < 0)
                  || (rule.get("max_dose") != null
                      && dose.compareTo(decimal(rule.get("max_dose"))) > 0)) hit = true;
            }
        }
        default -> {}
      }
      if (hit)
        results.add(
            result(ruleType, text(rule, "severity"), text(rule, "message"), rule.get("id")));
    }
    String status =
        results.stream().anyMatch(r -> r.get("severity").equals("BLOCK"))
            ? "BLOCKED"
            : results.stream().anyMatch(r -> r.get("severity").equals("INCOMPLETE"))
                ? "INCOMPLETE"
                : results.isEmpty() ? "CLEAR" : "WARNING";
    var response = Map.<String, Object>of("status", status, "results", results);
    try {
      db.update(
          "INSERT INTO ru_check(id,source_type,source_id,result,snapshot_json,checked_by) VALUES"
              + " (?,?,?,?,?,?)",
          IdWorker.getId(),
          type,
          source,
          status,
          json.writeValueAsString(
              Map.of("patientId", patient.get("id"), "lines", lines, "response", response)),
          access.current().id());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    return response;
  }

  public void assertAllowed(Map<String, Object> check, String reason) {
    String status = check.get("status").toString();
    if (Set.of("BLOCKED", "INCOMPLETE").contains(status))
      throw new BusinessException("RATIONAL_CHECK_BLOCKED", "用药审查阻断或信息不足，请处理后重试");
    if (status.equals("WARNING") && (reason == null || reason.isBlank()))
      throw new BusinessException("WARNING_REASON_REQUIRED", "请填写普通用药警告的处理理由");
  }

  private Map<String, Object> result(String type, String severity, String message, Object rule) {
    return Map.of("type", type, "severity", severity, "message", message, "ruleId", rule);
  }
}
