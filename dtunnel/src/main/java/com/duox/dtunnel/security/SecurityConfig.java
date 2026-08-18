package com.duox.dtunnel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * detail.md §8: /api/v1/* uses session-cookie auth (web UI);
 * /agent/v1/* uses device-credential auth (agents).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final AgentTokenFilter agentTokenFilter;

  public SecurityConfig(AgentTokenFilter agentTokenFilter) {
    this.agentTokenFilter = agentTokenFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      // CSRF: both APIs are JSON-only bearer/session APIs. Cross-site form
      // POSTs are already blocked by SameSite=Lax session cookies (§15) and
      // no cross-origin CORS is enabled, so the classic CSRF vector does not
      // apply; agents and the frps plugin carry their own credentials.
      .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**", "/agent/v1/**", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**"))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**", "/actuator/health", "/actuator/info",
            // Prometheus scrape endpoint (Milestone 4.4): metrics only, no
            // secrets; production restricts this via network policy, not auth.
            "/actuator/prometheus",
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
        .requestMatchers("/agent/v1/register", "/agent/v1/frp-plugin").permitAll() // own auth inside
        .requestMatchers("/agent/v1/**").authenticated()
        .requestMatchers("/api/v1/**").authenticated()
        .anyRequest().denyAll())
      .addFilterBefore(agentTokenFilter, UsernamePasswordAuthenticationFilter.class)
      .formLogin(form -> form.disable())
      .httpBasic(basic -> basic.disable())
      .logout(logout -> logout.logoutUrl("/api/v1/auth/logout").logoutSuccessUrl("/api/v1/auth/logout?done").permitAll())
      .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
          new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
          request -> request.getRequestURI().startsWith("/agent/v1")
              || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
              || request.getRequestURI().startsWith("/api/v1")));
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
