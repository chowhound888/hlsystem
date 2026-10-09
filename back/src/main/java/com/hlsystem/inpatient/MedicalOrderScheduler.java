package com.hlsystem.inpatient;

import com.hlsystem.security.DatabaseUserService;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MedicalOrderScheduler {
  private final JdbcTemplate db;
  private final DatabaseUserService users;
  private final InpatientService service;

  public MedicalOrderScheduler(
      JdbcTemplate db, DatabaseUserService users, InpatientService service) {
    this.db = db;
    this.users = users;
    this.service = service;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void generate() {
    var locations =
        db.queryForList(
            "SELECT DISTINCT location_id FROM ip_medical_order WHERE status='ACTIVE' AND"
                + " start_date<=CURDATE() AND (end_date IS NULL OR end_date>=CURDATE())",
            Long.class);
    if (locations.isEmpty()) return;
    try {
      var user = users.loadUserByUsername("admin");
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
      for (long location : locations)
        try {
          service.generate(location, LocalDate.now());
        } catch (RuntimeException e) {
          log.warn("Medical order generation failed for location {}: {}", location, e.getMessage());
        }
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
