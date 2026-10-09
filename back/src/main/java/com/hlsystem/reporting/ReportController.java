package com.hlsystem.reporting;

import com.hlsystem.common.BusinessException;
import com.hlsystem.inventory.InventoryService;
import com.hlsystem.security.AccessService;
import java.io.IOException;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
  private final JdbcTemplate db;
  private final AccessService access;
  private final InventoryService inventory;

  public ReportController(JdbcTemplate db, AccessService access, InventoryService inventory) {
    this.db = db;
    this.access = access;
    this.inventory = inventory;
  }

  @GetMapping("/summary")
  com.hlsystem.common.ApiResponse<?> summary(
      @RequestParam long locationId, @RequestParam String from, @RequestParam String to) {
    access.require("report:read");
    access.location(locationId);
    var start = java.time.LocalDate.parse(from);
    var end = java.time.LocalDate.parse(to);
    if (end.isBefore(start)) throw new BusinessException("VALIDATION_FAILED", "结束日期早于开始日期");
    String joins =
        " FROM inv_ledger l JOIN inv_stock s ON s.id=l.stock_id WHERE s.location_id=? AND"
            + " l.business_date BETWEEN ? AND ?";
    var sums =
        db.queryForMap(
            "SELECT COALESCE(SUM(CASE WHEN cost_delta>0 THEN cost_delta ELSE 0 END),0)"
                + " incoming,COALESCE(SUM(CASE WHEN cost_delta<0 THEN -cost_delta ELSE 0 END),0)"
                + " outgoing"
                + joins,
            locationId,
            start,
            end);
    var result = new HashMap<String, Object>(sums);
    result.put(
        "currentCost",
        db.queryForObject(
            "SELECT COALESCE(SUM(remaining_cost),0) FROM inv_stock WHERE location_id=?",
            java.math.BigDecimal.class,
            locationId));
    result.put(
        "prescriptionCount",
        db.queryForObject(
            "SELECT COUNT(*) FROM op_prescription WHERE location_id=? AND status='DISPENSED' AND"
                + " DATE(created_at) BETWEEN ? AND ?",
            Long.class,
            locationId,
            start,
            end));
    result.put(
        "daily",
        db.queryForList(
            "SELECT l.business_date date,SUM(CASE WHEN cost_delta>0 THEN cost_delta ELSE 0 END)"
                + " incoming,SUM(CASE WHEN cost_delta<0 THEN -cost_delta ELSE 0 END) outgoing"
                + joins
                + " GROUP BY l.business_date ORDER BY l.business_date",
            locationId,
            start,
            end));
    result.put(
        "items",
        db.queryForList(
            "SELECT i.name,i.specification,u.name unit,SUM(CASE WHEN l.qty_delta>0 THEN l.qty_delta"
                + " ELSE 0 END) incomingQty,SUM(CASE WHEN l.qty_delta<0 THEN -l.qty_delta ELSE 0"
                + " END) outgoingQty,SUM(l.cost_delta) cost FROM inv_ledger l JOIN inv_stock s ON"
                + " s.id=l.stock_id JOIN inv_cost_layer c ON c.id=s.cost_layer_id JOIN inv_batch b"
                + " ON b.id=c.batch_id JOIN md_item i ON i.id=b.item_id JOIN md_unit u ON"
                + " u.id=i.base_unit_id WHERE s.location_id=? AND l.business_date BETWEEN ? AND ?"
                + " GROUP BY i.id,i.name,i.specification,u.name ORDER BY i.code",
            locationId,
            start,
            end));
    return com.hlsystem.common.ApiResponse.ok(result);
  }

  @GetMapping("/{type}/export")
  ResponseEntity<byte[]> export(@PathVariable String type, @RequestParam long locationId)
      throws IOException {
    access.require("inventory:read");
    access.location(locationId);
    List<Map<String, Object>> rows;
    rows =
        switch (type) {
          case "stocks" -> inventory.stocks(locationId, "");
          case "documents" -> inventory.documents(locationId);
          case "ledger" ->
              db.queryForList(
                  "SELECT l.business_date 日期,i.name 药品,b.batch_no 批号,d.number 单据号,l.qty_delta"
                      + " 数量变化,l.balance_qty 结存,l.cost_delta 成本变化,l.balance_cost 结存成本 FROM"
                      + " inv_ledger l JOIN inv_stock s ON s.id=l.stock_id JOIN inv_cost_layer c ON"
                      + " c.id=s.cost_layer_id JOIN inv_batch b ON b.id=c.batch_id JOIN md_item i"
                      + " ON i.id=b.item_id JOIN inv_document_item x ON x.id=l.document_item_id"
                      + " JOIN inv_document d ON d.id=x.document_id WHERE s.location_id=? ORDER BY"
                      + " l.occurred_at LIMIT 10000",
                  locationId);
          default -> throw new BusinessException("NOT_FOUND", "报表类型不存在", HttpStatus.NOT_FOUND);
        };
    try (var book = new XSSFWorkbook();
        var output = new java.io.ByteArrayOutputStream()) {
      var sheet = book.createSheet("业务报表");
      if (!rows.isEmpty()) {
        var columns = new ArrayList<>(rows.getFirst().keySet());
        var header = sheet.createRow(0);
        for (int c = 0; c < columns.size(); c++) header.createCell(c).setCellValue(columns.get(c));
        for (int n = 0; n < rows.size(); n++) {
          var row = sheet.createRow(n + 1);
          for (int c = 0; c < columns.size(); c++) {
            Object value = rows.get(n).get(columns.get(c));
            String text = value == null ? "" : value.toString();
            if (text.matches("^[=+@-].*")) text = "'" + text;
            row.createCell(c).setCellValue(text);
          }
        }
        for (int c = 0; c < columns.size(); c++) sheet.setColumnWidth(c, 22 * 256);
        sheet.createFreezePane(0, 1);
      }
      book.write(output);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + ".xlsx")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(output.toByteArray());
    }
  }
}
