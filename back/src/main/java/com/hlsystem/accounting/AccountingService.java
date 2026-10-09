package com.hlsystem.accounting;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.common.BusinessException;
import com.hlsystem.common.Values;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingService {
  private final JdbcTemplate db;

  public AccountingService(JdbcTemplate db) {
    this.db = db;
  }

  public void openPeriod() {
    LocalDate date = LocalDate.now();
    db.update(
        "INSERT INTO acc_period(period_date,status) VALUES (?,'OPEN') ON DUPLICATE KEY UPDATE"
            + " period_date=VALUES(period_date)",
        date);
    if (!"OPEN"
        .equals(
            db.queryForObject(
                "SELECT status FROM acc_period WHERE period_date=? FOR UPDATE",
                String.class,
                date))) throw new BusinessException("PERIOD_CLOSED", "当前业务日已结账");
  }

  @Transactional
  public void post(
      String event,
      String type,
      long source,
      String description,
      BigDecimal amount,
      long debit,
      long credit,
      Long location) {
    amount = Values.money(amount);
    if (amount.signum() == 0) return;
    if (amount.signum() < 0) throw new BusinessException("INVALID_AMOUNT", "凭证金额不能为负");
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " acc_voucher(id,number,event_key,source_type,source_id,business_date,description)"
            + " VALUES (?,?,?,?,?,?,?)",
        id,
        "V" + id,
        event,
        type,
        source,
        LocalDate.now(),
        description);
    db.update(
        "INSERT INTO acc_voucher_entry(id,voucher_id,account_id,location_id,debit,credit) VALUES"
            + " (?,?,?,?,?,0)",
        IdWorker.getId(),
        id,
        debit,
        location,
        amount);
    db.update(
        "INSERT INTO acc_voucher_entry(id,voucher_id,account_id,location_id,debit,credit) VALUES"
            + " (?,?,?,?,0,?)",
        IdWorker.getId(),
        id,
        credit,
        location,
        amount);
  }
}
