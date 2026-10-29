package com.hlsystem.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaForwardFilter implements Filter {
  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    String path = request.getRequestURI();

    // API、静态资源、根路径直接放行
    if (path.startsWith("/api/")
        || path.startsWith("/assets/")
        || path.equals("/")
        || path.contains(".")) {
      chain.doFilter(req, res);
      return;
    }

    // SPA 路由回退
    request.getRequestDispatcher("/index.html").forward(req, res);
  }
}
