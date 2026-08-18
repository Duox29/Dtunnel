package com.duox.dtunnel.config;

import com.duox.dtunnel.security.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the rate-limit filter BEFORE Spring Security
 * (security chain runs at order -100) so abusive traffic is shed early.
 */
@Configuration
public class RateLimitConfig {

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitRegistration(RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
    reg.setOrder(-200);
    reg.addUrlPatterns("/api/v1/*", "/agent/v1/*");
    return reg;
  }
}
