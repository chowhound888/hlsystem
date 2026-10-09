package com.hlsystem.rational;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rational")
public class RationalController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final AuditService audit;

  public RationalController(JdbcTemplate db, AccessService access, AuditService audit) {
    this.db = db;
    this.access = access;
    this.audit = audit;
  }

  @GetMapping("/rules")
  ApiResponse<?> rules() {
    access.require("rational:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT r.*,i.name drugName,p.name peerName FROM ru_rule r JOIN md_item i ON"
                + " i.id=r.item_id LEFT JOIN md_item p ON p.id=r.peer_item_id ORDER BY r.code"));
  }

  @PostMapping("/rules")
  @Transactional
  ApiResponse<?> create(@RequestBody Map<String, Object> r) {
    access.require("rational:write");
    String type = text(r, "type"), severity = text(r, "severity");
    if (!Set.of("INTERACTION", "DOSE", "CONTRAINDICATION", "INDICATION").contains(type)
        || !Set.of("BLOCK", "WARNING").contains(severity)
        || text(r, "name").isBlank()
        || text(r, "message").isBlank())
      throw new BusinessException("VALIDATION_FAILED", "请填写有效规则类型、名称、严重度和说明");
    if (type.equals("INTERACTION") && r.get("peerItemId") == null)
      throw new BusinessException("VALIDATION_FAILED", "相互作用须指定另一药品");
    if (Set.of("CONTRAINDICATION", "INDICATION").contains(type) && r.get("diagnosisId") == null)
      throw new BusinessException("VALIDATION_FAILED", "请指定诊断");
    if (type.equals("DOSE") && r.get("minDose") == null && r.get("maxDose") == null)
      throw new BusinessException("VALIDATION_FAILED", "剂量规则至少设置一个上下限");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " ru_rule(id,code,type,name,item_id,peer_item_id,diagnosis_id,min_dose,max_dose,dose_unit,severity,message,source_note,min_age,max_age,dose_basis,per_kg,route)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        "RU" + id,
        type,
        text(r, "name"),
        id(r.get("itemId")),
        r.get("peerItemId"),
        r.get("diagnosisId"),
        r.get("minDose"),
        r.get("maxDose"),
        text(r, "doseUnit"),
        severity,
        text(r, "message"),
        text(r, "sourceNote"),
        r.get("minAge"),
        r.get("maxAge"),
        text(r, "doseBasis").isBlank() ? "DAILY" : text(r, "doseBasis"),
        Boolean.TRUE.equals(r.get("perKg")),
        r.get("route"));
    audit.record("CREATE", "RULE", id, null);
    return ApiResponse.ok(Map.of("id", id));
  }

  @PostMapping("/rules/{id}/enabled")
  @Transactional
  ApiResponse<?> enabled(@PathVariable long id, @RequestBody Map<String, Object> r) {
    access.require("rational:write");
    db.update(
        "UPDATE ru_rule SET enabled=?,revision=revision+1 WHERE id=?",
        Boolean.TRUE.equals(r.get("enabled")),
        id);
    audit.record("RULE_STATUS", "RULE", id, null);
    return ApiResponse.ok(null);
  }

  @GetMapping("/knowledge")
  ApiResponse<?> knowledge() {
    access.require("rational:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT k.*,i.name drugName FROM ru_knowledge k JOIN md_item i ON i.id=k.item_id ORDER"
                + " BY k.id"));
  }

  @PostMapping("/knowledge")
  @Transactional
  ApiResponse<?> knowledgeCreate(@RequestBody Map<String, Object> r) {
    access.require("rational:write");
    if (text(r, "title").isBlank() || text(r, "content").isBlank())
      throw new BusinessException("VALIDATION_FAILED", "请填写标题和内容");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO ru_knowledge(id,item_id,title,content,source_note) VALUES (?,?,?,?,?)",
        id,
        id(r.get("itemId")),
        text(r, "title"),
        text(r, "content"),
        text(r, "sourceNote"));
    audit.record("CREATE", "KNOWLEDGE", id, null);
    return ApiResponse.ok(Map.of("id", id));
  }

  @PostMapping("/allergies")
  @Transactional
  ApiResponse<?> allergy(@RequestBody Map<String, Object> r) {
    access.require("clinical:write");
    if (text(r, "reaction").isBlank()) throw new BusinessException("VALIDATION_FAILED", "请填写过敏反应");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO cl_patient_allergy(id,patient_id,item_id,reaction) VALUES (?,?,?,?)",
        id,
        id(r.get("patientId")),
        id(r.get("itemId")),
        text(r, "reaction"));
    audit.record("CREATE", "ALLERGY", id, null);
    return ApiResponse.ok(null);
  }
}
