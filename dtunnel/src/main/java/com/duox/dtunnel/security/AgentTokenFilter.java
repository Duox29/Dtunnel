package com.duox.dtunnel.security;

import com.duox.dtunnel.domain.Agent;
import com.duox.dtunnel.domain.AgentStatus;
import com.duox.dtunnel.repo.AgentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Resolves "Authorization: Bearer <device-token>" on /agent/v1/** into an
 * AgentPrincipal. Revoked agents fail here on every request, which bounds
 * revocation latency to a single round-trip (detail.md §4, §15).
 */
@Component
public class AgentTokenFilter extends OncePerRequestFilter {

  private final AgentTokenService tokens;
  private final AgentRepository agents;

  public AgentTokenFilter(AgentTokenService tokens, AgentRepository agents) {
    this.tokens = tokens;
    this.agents = agents;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/agent/v1/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      UUID agentId = tokens.resolve(token);
      if (agentId != null) {
        Agent agent = agents.findById(agentId).orElse(null);
        if (agent != null && agent.getStatus() != AgentStatus.REVOKED) {
          tokens.touch(token);
          var auth = new AgentAuthentication(AgentPrincipal.of(agent));
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      }
    }
    chain.doFilter(request, response);
  }

  public static class AgentAuthentication extends AbstractAuthenticationToken {
    private final AgentPrincipal principal;

    public AgentAuthentication(AgentPrincipal principal) {
      super(List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
      this.principal = principal;
      setAuthenticated(true);
    }

    @Override
    public Object getCredentials() { return null; }

    @Override
    public AgentPrincipal getPrincipal() { return principal; }
  }
}
