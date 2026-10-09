package com.hlsystem.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserService implements UserDetailsService {
  private final JdbcTemplate db;

  public DatabaseUserService(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public LoginUser loadUserByUsername(String username) {
    var users =
        db.query(
            "SELECT * FROM sys_user WHERE username=?",
            (rs, n) ->
                new LoginUser(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("display_name"),
                    rs.getLong("department_id"),
                    rs.getBoolean("enabled"),
                    db.queryForList(
                        "SELECT DISTINCT p.code FROM sys_permission p JOIN sys_role_permission rp"
                            + " ON p.id=rp.permission_id JOIN sys_user_role ur ON"
                            + " ur.role_id=rp.role_id WHERE ur.user_id=?",
                        String.class,
                        rs.getLong("id")),
                    db.queryForList(
                        "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON r.id=ur.role_id"
                            + " WHERE ur.user_id=?",
                        String.class,
                        rs.getLong("id")),
                    db.queryForList(
                        "SELECT location_id FROM sys_user_scope WHERE user_id=?",
                        Long.class,
                        rs.getLong("id"))),
            username);
    if (users.isEmpty()) throw new UsernameNotFoundException("账号不存在");
    return users.getFirst();
  }
}
