package com.hlsystem.system;

import static com.hlsystem.common.Values.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hlsystem.common.*;
import com.hlsystem.security.AccessService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system/print-templates")
public class PrintTemplateController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final ObjectMapper json;
  private final AuditService audit;

  public PrintTemplateController(
      JdbcTemplate db, AccessService access, ObjectMapper json, AuditService audit) {
    this.db = db;
    this.access = access;
    this.json = json;
    this.audit = audit;
  }

  @GetMapping
  ApiResponse<?> list() {
    return ApiResponse.ok(db.queryForList("SELECT * FROM sys_print_template ORDER BY id"));
  }

  @PostMapping("/{id}")
  @Transactional
  ApiResponse<?> save(@PathVariable long id, @RequestBody Map<String, Object> r)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    access.require("system:write");
    String title = text(r, "title"), orientation = text(r, "orientation");
    if (title.isBlank()
        || title.length() > 200
        || !Set.of("portrait", "landscape").contains(orientation)
        || !(r.get("fields") instanceof List<?> fields))
      throw new BusinessException("VALIDATION_FAILED", "模板参数无效");
    Set<String> allowed =
        Set.of(
            "name",
            "specification",
            "unit",
            "quantity",
            "batch_no",
            "cost_amount",
            "directions",
            "amount");
    if (fields.size() != new HashSet<>(fields).size()
        || !allowed.containsAll(fields)
        || !fields.contains("name")
        || !fields.contains("quantity")
        || (!fields.contains("amount") && !fields.contains("cost_amount")))
      throw new BusinessException("VALIDATION_FAILED", "模板必须保留名称、数量和金额，且列不可重复");
    db.update(
        "UPDATE sys_print_template SET"
            + " title=?,paper_orientation=?,fields_json=?,revision=revision+1 WHERE id=?",
        title,
        orientation,
        json.writeValueAsString(fields),
        id);
    audit.record("UPDATE", "PRINT_TEMPLATE", id, null);
    return ApiResponse.ok(null);
  }
}
