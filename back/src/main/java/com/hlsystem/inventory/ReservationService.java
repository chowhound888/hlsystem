package com.hlsystem.inventory;

import static com.hlsystem.common.Values.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {
  private final JdbcTemplate db;
  private final InventoryService inventory;

  public ReservationService(JdbcTemplate db, InventoryService inventory) {
    this.db = db;
    this.inventory = inventory;
  }

  public void reserve(long location, long item, long prescriptionItem, BigDecimal qty) {
    inventory.checkStocktake(location);
    var rows =
        db.queryForList(
            "SELECT s.* FROM inv_stock s JOIN inv_cost_layer c ON c.id=s.cost_layer_id JOIN"
                + " inv_batch b ON b.id=c.batch_id WHERE s.location_id=? AND b.item_id=? AND"
                + " s.stock_status='AVAILABLE' AND s.frozen=FALSE AND b.quality_status='PASSED' AND"
                + " b.expires_on>=? AND s.on_hand_qty>s.reserved_qty ORDER BY"
                + " b.expires_on,c.created_at,c.id FOR UPDATE",
            location,
            item,
            LocalDate.now());
    BigDecimal available =
        rows.stream()
            .map(s -> decimal(s.get("on_hand_qty")).subtract(decimal(s.get("reserved_qty"))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (available.compareTo(qty) < 0)
      throw new BusinessException("STOCK_INSUFFICIENT", "划价预留失败，药品可用库存不足");
    BigDecimal left = qty;
    for (var s : rows) {
      if (left.signum() == 0) break;
      BigDecimal take =
          left.min(decimal(s.get("on_hand_qty")).subtract(decimal(s.get("reserved_qty"))));
      db.update("UPDATE inv_stock SET reserved_qty=reserved_qty+? WHERE id=?", take, s.get("id"));
      db.update(
          "INSERT INTO inv_stock_reservation(id,prescription_item_id,stock_id,base_qty,expires_at)"
              + " VALUES (?,?,?,?,?)",
          IdWorker.getId(),
          prescriptionItem,
          s.get("id"),
          take,
          LocalDateTime.now().plusMinutes(30));
      left = left.subtract(take);
    }
  }

  public void release(long prescription, String status) {
    for (var r :
        db.queryForList(
            "SELECT r.* FROM inv_stock_reservation r JOIN op_prescription_item x ON"
                + " x.id=r.prescription_item_id WHERE x.prescription_id=? AND r.status='ACTIVE'"
                + " ORDER BY r.stock_id FOR UPDATE",
            prescription)) {
      db.queryForMap("SELECT * FROM inv_stock WHERE id=? FOR UPDATE", r.get("stock_id"));
      db.update(
          "UPDATE inv_stock SET reserved_qty=reserved_qty-? WHERE id=?",
          r.get("base_qty"),
          r.get("stock_id"));
      db.update("UPDATE inv_stock_reservation SET status=? WHERE id=?", status, r.get("id"));
    }
  }
}
