package com.duox.dtunnel.api;

import com.duox.dtunnel.application.AgentService;
import com.duox.dtunnel.application.ApiException;
import com.duox.dtunnel.application.AuditService;
import com.duox.dtunnel.application.DesiredStateService;
import com.duox.dtunnel.application.UsageService;
import com.duox.dtunnel.domain.Agent;
import com.duox.dtunnel.domain.AgentStatus;
import com.duox.dtunnel.domain.Tunnel;
import com.duox.dtunnel.domain.TunnelStatus;
import com.duox.dtunnel.domain.User;
import com.duox.dtunnel.repo.AgentRepository;
import com.duox.dtunnel.repo.TunnelRepository;
import com.duox.dtunnel.repo.UserRepository;
import com.duox.dtunnel.security.AgentPrincipal;
import com.duox.dtunnel.security.AgentTokenService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * detail.md §8: /agent/v1/* — device-credential auth.
 * register is open (authenticates with user email/password, §6);
 * everything else requires the bearer device token (AgentTokenFilter).
 */
@RestController
@RequestMapping("/agent/v1")
public class AgentApiController {

  public record RegisterRequest(@Email @NotBlank String email, @NotBlank String password,
                                @NotBlank String publicKey, String platform, String agentVersion) {}
  public record HeartbeatRequest(Integer appliedVersion, String agentVersion,
                                 List<TunnelReport> tunnels) {}
  /** Sampled traffic counters per tunnel (detail.md Milestone 3.3). */
  public record TunnelReport(String tunnelId, String status,
                             Long bytesIn, Long bytesOut, Integer activeSeconds) {}

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AgentService agentService;
  private final AgentRepository agents;
  private final AgentTokenService tokens;
  private final DesiredStateService desiredState;
  private final TunnelRepository tunnels;
  private final AuditService audit;
  private final UsageService usageService;

  public AgentApiController(UserRepository users, PasswordEncoder encoder, AgentService agentService,
                            AgentRepository agents, AgentTokenService tokens,
                            DesiredStateService desiredState, TunnelRepository tunnels, AuditService audit,
                            UsageService usageService) {
    this.users = users;
    this.encoder = encoder;
    this.agentService = agentService;
    this.agents = agents;
    this.tokens = tokens;
    this.desiredState = desiredState;
    this.tunnels = tunnels;
    this.audit = audit;
    this.usageService = usageService;
  }

  /** detail.md §6: first-run registration binds the device public key to a user account. */
  @PostMapping("/register")
  public Map<String, Object> register(@jakarta.validation.Valid @RequestBody RegisterRequest req) {
    User u = users.findByEmailIgnoreCase(req.email().trim())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    if (u.getPasswordHash() == null || !encoder.matches(req.password(), u.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials");
    }
    Agent a = agentService.register(u.getId(), req.publicKey(), req.platform(), req.agentVersion());
    String token = tokens.issue(a);
    return Map.of("agentId", a.getId().toString(), "token", token, "status", a.getStatus().name());
  }

  /** detail.md §8: current desired version number only — cheap poll. */
  @GetMapping("/config/version")
  public Map<String, Object> configVersion(@AuthenticationPrincipal Principal principal) {
    AgentPrincipal agent = (AgentPrincipal) principal;
    Agent a = agents.findById(agent.agentId()).orElseThrow();
    return Map.of("version", desiredState.currentVersion(agent.agentId()),
        "status", a.getStatus().name());
  }

  /** detail.md §8: full desired-state payload. Empty until the agent is approved (ONLINE). */
  @GetMapping("/config")
  public Map<String, Object> config(@AuthenticationPrincipal Principal principal) {
    AgentPrincipal agent = (AgentPrincipal) principal;
    Agent a = agents.findById(agent.agentId()).orElseThrow();
    if (a.getStatus() == AgentStatus.REVOKED) throw new ApiException(HttpStatus.FORBIDDEN, "agent revoked");
    Map<String, Object> payload = a.getStatus() == AgentStatus.ONLINE
        ? desiredState.buildPayload(agent.agentId())
        : Map.of("agentId", agent.agentId().toString(), "proxies", List.of());
    return Map.of("version", desiredState.currentVersion(agent.agentId()), "payload", payload);
  }

  /**
   * detail.md §8 + §11: liveness + reported running state. Feeds the
   * reconciliation loop: STARTING→ACTIVE when the agent reports RUNNING,
   * STOPPING→STOPPED when it reports the tunnel down.
   */
  @PostMapping("/heartbeat")
  @Transactional
  public Map<String, Object> heartbeat(@AuthenticationPrincipal Principal principal,
                                       @RequestBody(required = false) HeartbeatRequest req) {
    AgentPrincipal agentP = (AgentPrincipal) principal;
    Agent a = agents.findById(agentP.agentId()).orElseThrow();
    a.setLastSeenAt(Instant.now());
    if (req != null && req.agentVersion() != null) a.setAgentVersion(req.agentVersion());
    agents.save(a);

    if (req != null && req.tunnels() != null) {
      for (TunnelReport r : req.tunnels()) {
        UUID tid;
        try { tid = UUID.fromString(r.tunnelId()); } catch (RuntimeException e) { continue; }
        Tunnel t = tunnels.findById(tid).orElse(null);
        if (t == null || !t.getAgentId().equals(a.getId())) continue;
        String s = r.status() == null ? "" : r.status().toUpperCase();
        switch (s) {
          case "RUNNING", "ACTIVE" -> {
            if (t.getStatus() == TunnelStatus.STARTING || t.getStatus() == TunnelStatus.CONFIGURED) {
              t.setStatus(TunnelStatus.ACTIVE);
              tunnels.save(t);
              audit.log(a.getId().toString(), "AGENT", "tunnel.active", "tunnel", tid.toString(), "SUCCESS", null);
            }
          }
          case "STOPPED" -> {
            if (t.getStatus() == TunnelStatus.STOPPING) {
              t.setStatus(TunnelStatus.STOPPED);
              tunnels.save(t);
              audit.log(a.getId().toString(), "AGENT", "tunnel.stopped", "tunnel", tid.toString(), "SUCCESS", null);
            }
          }
          case "ERROR" -> {
            if (t.getStatus() != TunnelStatus.ERROR) {
              t.setStatus(TunnelStatus.ERROR);
              tunnels.save(t);
              audit.log(a.getId().toString(), "AGENT", "tunnel.error", "tunnel", tid.toString(), "SUCCESS", null);
            }
          }
          default -> { }
        }
        // usage metering: record sampled counters when the agent reports them
        if (r.bytesIn() != null || r.bytesOut() != null) {
          try {
            usageService.record(a.getId(), tid,
                r.bytesIn() == null ? 0 : r.bytesIn(),
                r.bytesOut() == null ? 0 : r.bytesOut(),
                r.activeSeconds() == null ? 0 : r.activeSeconds());
          } catch (RuntimeException ignored) {
            // usage is best-effort; never fail the heartbeat over it
          }
        }
      }
    }
    return Map.of("desiredVersion", desiredState.currentVersion(a.getId()),
        "serverTime", Instant.now().toString());
  }
}
