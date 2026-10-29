package com.hlsystem.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hlsystem.common.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper json) throws Exception {
    http.authorizeHttpRequests(
        a ->
            a.requestMatchers("/api/v1/auth/csrf", "/api/v1/health")
                .permitAll()
                .requestMatchers("/assets/**", "/favicon.ico", "/index.html")
                .permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/")
                .permitAll()
                .anyRequest()
                .authenticated());
    http.csrf(
        c ->
            c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/assets/**", "/favicon.ico", "/index.html", "/"));
    http.formLogin(
        f ->
            f.loginProcessingUrl("/api/v1/auth/login")
                .permitAll()
                .successHandler(
                    (req, res, auth) -> {
                      res.setContentType("application/json;charset=UTF-8");
                      json.writeValue(res.getWriter(), ApiResponse.ok(null));
                    })
                .failureHandler(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setContentType("application/json;charset=UTF-8");
                      json.writeValue(
                          res.getWriter(), new ApiResponse<>("LOGIN_FAILED", "账号或密码错误", null));
                    }));
    http.logout(
        l ->
            l.logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler(
                    (req, res, auth) -> {
                      res.setContentType("application/json;charset=UTF-8");
                      json.writeValue(res.getWriter(), ApiResponse.ok(null));
                    }));
    http.exceptionHandling(
        e ->
            e.authenticationEntryPoint(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setContentType("application/json;charset=UTF-8");
                      json.writeValue(
                          res.getWriter(), new ApiResponse<>("UNAUTHENTICATED", "请先登录", null));
                    })
                .accessDeniedHandler(
                    (req, res, ex) -> {
                      res.setStatus(403);
                      res.setContentType("application/json;charset=UTF-8");
                      json.writeValue(
                          res.getWriter(), new ApiResponse<>("FORBIDDEN", "没有操作权限或会话校验失败", null));
                    }));
    return http.build();
  }
}
