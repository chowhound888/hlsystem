package com.hlsystem.security;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record LoginUser(
    Long id,
    String username,
    String password,
    String displayName,
    Long departmentId,
    boolean enabled,
    List<String> permissions,
    List<String> roles,
    List<Long> locationIds)
    implements UserDetails {
  @Override
  public List<? extends GrantedAuthority> getAuthorities() {
    return permissions.stream().map(SimpleGrantedAuthority::new).toList();
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
