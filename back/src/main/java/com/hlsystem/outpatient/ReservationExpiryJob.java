package com.hlsystem.outpatient;

import com.hlsystem.inventory.ReservationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ReservationExpiryJob {
  private final JdbcTemplate db;
  private final ReservationService reservations;
  private final TransactionTemplate tx;

  public ReservationExpiryJob(
      JdbcTemplate db,
      ReservationService reservations,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.db = db;
    this.reservations = reservations;
    this.tx = new TransactionTemplate(manager);
  }

  @Scheduled(fixedDelay = 60000)
  public void expire() {
    for (Long id :
        db.queryForList(
            "SELECT DISTINCT p.id FROM op_prescription p JOIN op_prescription_item x ON"
                + " x.prescription_id=p.id JOIN inv_stock_reservation r ON"
                + " r.prescription_item_id=x.id WHERE p.status='PRICED' AND r.status='ACTIVE' AND"
                + " r.expires_at<CURRENT_TIMESTAMP(3)",
            Long.class))
      tx.executeWithoutResult(
          s -> {
            String state =
                db.queryForObject(
                    "SELECT status FROM op_prescription WHERE id=? FOR UPDATE", String.class, id);
            if (!state.equals("PRICED")) return;
            reservations.release(id, "EXPIRED");
            db.update(
                "DELETE FROM inv_stock_reservation WHERE prescription_item_id IN (SELECT id FROM"
                    + " op_prescription_item WHERE prescription_id=?) AND status='EXPIRED'",
                id);
            db.update(
                "UPDATE op_prescription SET status='SUBMITTED',version=version+1 WHERE id=?", id);
          });
  }
}
