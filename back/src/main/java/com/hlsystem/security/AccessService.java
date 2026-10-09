package com.hlsystem.security;

import com.hlsystem.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("access")
public class AccessService {
  private final DatabaseUserService users;

  public AccessService(DatabaseUserService users) {
    this.users = users;
  }

  public LoginUser current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof LoginUser user))
      throw new BusinessException("UNAUTHENTICATED", "请先登录", HttpStatus.UNAUTHORIZED);
    var fresh = users.loadUserByUsername(user.username());
    if (!fresh.enabled())
      throw new BusinessException("UNAUTHENTICATED", "账号已停用", HttpStatus.UNAUTHORIZED);
    return fresh;
  }

  public void require(String permission) {
    if (!current().permissions().contains(permission))
      throw new BusinessException("FORBIDDEN", "没有操作权限", HttpStatus.FORBIDDEN);
  }

  public void location(Long id) {
    if (id == null || !current().locationIds().contains(id))
      throw new BusinessException("NOT_FOUND", "机构不存在或不可访问", HttpStatus.NOT_FOUND);
  }
}
