package com.hlsystem.accounting;

import com.hlsystem.security.DatabaseUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ScheduledPriceJob {
  private final JdbcTemplate db;
  private final DatabaseUserService users;
  private final PriceService prices;

  public ScheduledPriceJob(JdbcTemplate db, DatabaseUserService users, PriceService prices) {
    this.db = db;
    this.users = users;
    this.prices = prices;
  }

  @Scheduled(fixedDelay = 30000)
  public void run() {
    var ids =
        db.queryForList(
            "SELECT id FROM price_adjustment WHERE confirmation_mode='AUTO' AND status='APPROVED'"
                + " AND effective_at<=CURRENT_TIMESTAMP(3)",
            Long.class);
    if (ids.isEmpty()) return;
    String username =
        db.queryForObject(
            "SELECT u.username FROM sys_user u JOIN sys_config c ON c.config_value=CAST(u.id AS"
                + " CHAR) WHERE c.config_key='auto_price_job_user'",
            String.class);
    try {
      var u = users.loadUserByUsername(username);
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
      for (long id : ids)
        try {
          prices.apply(id);
        } catch (RuntimeException e) {
          log.warn("Scheduled price {} failed: {}", id, e.getMessage());
        }
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
