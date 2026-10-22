package com.hlsystem.system;

import com.hlsystem.inventory.InventoryService;
import com.hlsystem.security.DatabaseUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Order(30)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class ChartDataInitializer implements ApplicationRunner {
  private final JdbcTemplate db;
  private final DatabaseUserService users;
  private final InventoryService inventory;

  public ChartDataInitializer(
      JdbcTemplate db, DatabaseUserService users, InventoryService inventory) {
    this.db = db;
    this.users = users;
    this.inventory = inventory;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (db.queryForObject(
            "SELECT COUNT(*) FROM sys_config WHERE config_key='chart_demo_initialized'",
            Integer.class)
        > 0) return;

    var context = SecurityContextHolder.createEmptyContext();
    SecurityContextHolder.setContext(context);
    try {
      // 为每种药品在 location 1 创建近7天的出入库记录
      for (int dayOffset = 6; dayOffset >= 0; dayOffset--) {
        LocalDate date = LocalDate.now().minusDays(dayOffset);
        boolean isInbound = dayOffset % 2 == 0; // 偶数天入库，奇数天出库

        signIn("admin");

        if (isInbound) {
          // 入库
          var lines = new ArrayList<Map<String, Object>>();
          int maxDrug = Math.min(20, db.queryForObject("SELECT COUNT(*) FROM md_drug", Integer.class));
          for (int drugId = 1; drugId <= maxDrug; drugId++) {
            var item = db.queryForMap(
                "SELECT i.id, i.base_unit_id, COALESCE(d.retail_price, 1) price FROM md_item i LEFT JOIN md_drug d ON d.item_id=i.id WHERE i.id=?",
                drugId);
            int qty = 100 + (drugId * 37 + dayOffset * 13) % 200;
            BigDecimal cost = new BigDecimal(item.get("price").toString()).multiply(new BigDecimal("0.6")).setScale(2, java.math.RoundingMode.HALF_UP);
            lines.add(Map.of(
                "itemId", item.get("id"),
                "unitId", item.get("base_unit_id"),
                "quantity", String.valueOf(qty),
                "batchNo", "CHART-" + drugId + "-D" + dayOffset,
                "manufacturedOn", date.minusMonths(2).toString(),
                "expiresOn", date.plusDays(180 + (drugId * 30) % 365).toString(),
                "unitCost", cost.toPlainString(),
                "manufacturerId", (drugId % 4) + 1));
          }
          if (lines.isEmpty()) continue;
          // 检查是否已存在
          if (db.queryForObject(
                  "SELECT COUNT(*) FROM inv_document WHERE location_id=1 AND reason=? AND business_date=?",
                  Integer.class, "CHART-INBOUND-D" + dayOffset, date) > 0) continue;
          long docId = inventory.create(Map.of(
              "type", "RECEIPT", "locationId", 1L, "supplierId", 1L,
              "reason", "CHART-INBOUND-D" + dayOffset, "items", lines));
          inventory.action(docId, "submit", 0, "");
          signIn("warehouse2");
          inventory.action(docId, "approve", 1, "");
          signIn("admin");
          inventory.action(docId, "execute", 2, "");
        } else {
          // 出库（科室领用）
          var lines = new ArrayList<Map<String, Object>>();
          int maxDrug = Math.min(15, db.queryForObject("SELECT COUNT(*) FROM md_drug", Integer.class));
          for (int drugId = 1; drugId <= maxDrug; drugId++) {
            var stocks = db.queryForList(
                "SELECT s.on_hand_qty - s.reserved_qty avail FROM inv_stock s JOIN inv_cost_layer c ON c.id=s.cost_layer_id JOIN inv_batch b ON b.id=c.batch_id WHERE b.item_id=? AND s.location_id=1 AND s.stock_status='AVAILABLE' AND s.on_hand_qty > s.reserved_qty ORDER BY b.expires_on LIMIT 1",
                drugId);
            if (stocks.isEmpty() || ((Number) stocks.get(0).get("avail")).intValue() <= 0) continue;
            int avail = ((Number) stocks.get(0).get("avail")).intValue();
            int qty = Math.min(avail, 20 + (drugId * 11 + dayOffset * 7) % 50);
            if (qty <= 0) continue;
            var item = db.queryForMap("SELECT id, base_unit_id FROM md_item WHERE id=?", drugId);
            lines.add(Map.of(
                "itemId", item.get("id"),
                "unitId", item.get("base_unit_id"),
                "quantity", String.valueOf(qty)));
          }
          if (lines.isEmpty()) continue;
          long targetLocation = dayOffset % 2 == 1 ? 3L : 5L;
          if (db.queryForObject(
                  "SELECT COUNT(*) FROM inv_document WHERE location_id=1 AND reason=? AND business_date=?",
                  Integer.class, "CHART-OUTBOUND-D" + dayOffset, date) > 0) continue;
          long docId = inventory.create(Map.of(
              "type", "ISSUE", "locationId", 1L, "targetLocationId", targetLocation,
              "reason", "CHART-OUTBOUND-D" + dayOffset, "items", lines));
          inventory.action(docId, "submit", 0, "");
          signIn("warehouse2");
          inventory.action(docId, "approve", 1, "");
          signIn("admin");
          inventory.action(docId, "execute", 2, "");
        }
      }
      db.update("INSERT INTO sys_config VALUES ('chart_demo_initialized','true')");
    } catch (Exception e) {
      System.err.println("Chart data init warning: " + e.getMessage());
      // 不阻断启动
      db.update("INSERT IGNORE INTO sys_config VALUES ('chart_demo_initialized','partial')");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void signIn(String username) {
    var u = users.loadUserByUsername(username);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
  }
}
