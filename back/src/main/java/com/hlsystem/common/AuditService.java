package com.hlsystem.common;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hlsystem.security.AccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final JdbcTemplate db;
  private final AccessService access;

  public AuditService(JdbcTemplate db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  public void record(String action, String type, Long id, String reason) {
    db.update(
        "INSERT INTO sys_audit_log(id,user_id,action,source_type,source_id,reason) VALUES"
            + " (?,?,?,?,?,?)",
        IdWorker.getId(),
        access.current().id(),
        action,
        type,
        id,
        reason);
  }

  public void transition(
      String type, Long id, String from, String to, String action, String reason) {
    db.update(
        "INSERT INTO"
            + " sys_document_action(id,source_type,source_id,from_status,to_status,action,operator_id,reason)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        IdWorker.getId(),
        type,
        id,
        from,
        to,
        action,
        access.current().id(),
        reason);
    record(action, type, id, reason);
  }
}
