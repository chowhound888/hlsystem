package com.hlsystem.master.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.ApiResponse;
import com.hlsystem.common.AuditService;
import com.hlsystem.common.BusinessException;
import com.hlsystem.security.AccessService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final AuditService audit;
  private static final Map<String, String> TABLES =
      Map.of(
          "units",
          "md_unit",
          "manufacturers",
          "md_manufacturer",
          "suppliers",
          "md_supplier",
          "diagnoses",
          "md_diagnosis");

  public CatalogController(JdbcTemplate db, AccessService access, AuditService audit) {
    this.db = db;
    this.access = access;
    this.audit = audit;
  }

  @GetMapping("/master/options")
  ApiResponse<?> options() {
    access.require("master:read");
    return ApiResponse.ok(
        Map.of(
            "units",
            db.queryForList("SELECT * FROM md_unit ORDER BY id"),
            "manufacturers",
            db.queryForList("SELECT * FROM md_manufacturer WHERE enabled=TRUE"),
            "suppliers",
            db.queryForList("SELECT * FROM md_supplier WHERE enabled=TRUE"),
            "diagnoses",
            db.queryForList("SELECT * FROM md_diagnosis")));
  }

  @GetMapping("/master/catalogs/{type}")
  ApiResponse<?> list(@PathVariable String type) {
    access.require("master:read");
    return ApiResponse.ok(db.queryForList("SELECT * FROM " + table(type) + " ORDER BY code"));
  }

  @PostMapping("/master/catalogs/{type}")
  @Transactional
  ApiResponse<?> create(@PathVariable String type, @RequestBody Map<String, String> r) {
    access.require("master:write");
    String t = table(type);
    String code = r.get("code"), name = r.get("name");
    if (code == null
        || code.isBlank()
        || code.length() > 64
        || name == null
        || name.isBlank()
        || name.length() > 200) throw new BusinessException("VALIDATION_FAILED", "请填写有效的编码和名称");
    long id = IdWorker.getId();
    db.update("INSERT INTO " + t + "(id,code,name) VALUES (?,?,?)", id, code.trim(), name.trim());
    audit.record("CREATE", type, id, null);
    return ApiResponse.ok(Map.of("id", id));
  }

  @GetMapping("/system/locations")
  ApiResponse<?> locations() {
    return ApiResponse.ok(
        access.current().locationIds().stream()
            .map(
                id ->
                    db.queryForMap(
                        "SELECT l.*,d.name department FROM sys_location l JOIN sys_department d ON"
                            + " d.id=l.department_id WHERE l.id=?",
                        id))
            .toList());
  }

  @GetMapping("/master/transfer-locations")
  ApiResponse<?> destinations() {
    access.require("master:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT id,name,type FROM sys_location WHERE enabled=TRUE AND type<>'TRANSIT' ORDER BY"
                + " id"));
  }

  @GetMapping("/master/items")
  ApiResponse<?> items() {
    access.require("master:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT i.*,i.id itemId,u.name unit FROM md_item i JOIN md_unit u ON"
                + " u.id=i.base_unit_id WHERE i.enabled=TRUE ORDER BY i.code"));
  }

  @GetMapping("/master/items/{id}")
  ApiResponse<?> item(@PathVariable long id) {
    access.require("master:read");
    var i = new HashMap<>(db.queryForMap("SELECT * FROM md_item WHERE id=?", id));
    i.put(
        "units",
        db.queryForList(
            "SELECT iu.unit_id unitId,iu.factor_to_base factor,u.name FROM md_item_unit iu JOIN"
                + " md_unit u ON u.id=iu.unit_id WHERE item_id=?",
            id));
    return ApiResponse.ok(i);
  }

  @PostMapping("/master/items")
  @Transactional
  ApiResponse<?> material(@RequestBody Map<String, Object> r) {
    access.require("master:write");
    String type = r.get("itemType").toString();
    if (!Set.of("RAW_MATERIAL", "AUXILIARY", "PACKAGING", "SEMI_FINISHED").contains(type)
        || r.get("name").toString().isBlank())
      throw new BusinessException("VALIDATION_FAILED", "物料类型或名称无效");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO md_item(id,code,name,specification,item_type,base_unit_id,quantity_scale)"
            + " VALUES (?,?,?,?,?,?,?)",
        id,
        r.get("code"),
        r.get("name"),
        r.get("specification"),
        type,
        com.hlsystem.common.Values.id(r.get("baseUnitId")),
        r.get("quantityScale"));
    db.update(
        "INSERT INTO md_item_unit(id,item_id,unit_id,factor_to_base) VALUES (?,?,?,1)",
        IdWorker.getId(),
        id,
        r.get("baseUnitId"));
    audit.record("CREATE", "MATERIAL", id, null);
    return ApiResponse.ok(Map.of("id", id));
  }

  @GetMapping("/system/departments")
  ApiResponse<?> departments() {
    return ApiResponse.ok(db.queryForList("SELECT * FROM sys_department ORDER BY id"));
  }

  @GetMapping("/system/users")
  ApiResponse<?> users() {
    access.require("system:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT u.id,u.username,u.display_name displayName,u.enabled,d.name"
                + " department,GROUP_CONCAT(r.name) roles FROM sys_user u JOIN sys_department d ON"
                + " d.id=u.department_id JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r"
                + " ON r.id=ur.role_id GROUP BY u.id,d.name ORDER BY u.id"));
  }

  @GetMapping("/system/audit-logs")
  ApiResponse<?> logs() {
    access.require("system:read");
    return ApiResponse.ok(
        db.queryForList(
            "SELECT a.*,u.display_name operator FROM sys_audit_log a JOIN sys_user u ON"
                + " u.id=a.user_id ORDER BY a.occurred_at DESC LIMIT 200"));
  }

  private String table(String type) {
    String t = TABLES.get(type);
    if (t == null) throw new BusinessException("NOT_FOUND", "资料类型不存在", HttpStatus.NOT_FOUND);
    return t;
  }
}
