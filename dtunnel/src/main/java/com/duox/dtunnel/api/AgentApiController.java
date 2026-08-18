package com.duox.dtunnel.api;

import com.duox.dtunnel.application.AgentService;
import com.duox.dtunnel.application.ApiException;
import com.duox.dtunnel.application.AuditService;
import com.duox.dtunnel.application.DesiredStateService;
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
  /**
   * Observed per-tunnel state. Usage metering is collected server-side from
   * the node's frps admin API (detail.md Milestone 3.3 + §1), not reported here.
   */
  public record TunnelReport(String tunnelId, String status) {}

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AgentService agentService;
  private final AgentRepository agents;
  private final AgentTokenService tokens;
  private final DesiredStateService desiredState;
  private final TunnelRepository tunnels;
  private final AuditService audit;
  private final com.duox.dtunnel.application.AgentChannelService channel;

  public AgentApiController(UserRepository users, PasswordEncoder encoder, AgentService agentService,
                            AgentRepository agents, AgentTokenService tokens,
                            DesiredStateService desiredState, TunnelRepository tunnels, AuditService audit,
                            com.duox.dtunnel.application.AgentChannelService channel) {
    this.users = users;
    this.encoder = encoder;
    this.agentService = agentService;
    this.agents = agents;
    this.tokens = tokens;
    this.desiredState = desiredState;
    this.tunnels = tunnels;
    this.audit = audit;
    this.channel = channel;
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
    // Single source of truth (§11): return the stored configuration version so
    // the version number always matches the payload the agent applies.
    return desiredState.storedConfig(agent.agentId());
  }

  /**
   * detail.md §8 + §11: liveness + reported running state. Feeds the
   * reconciliation loop: STARTING→ACTIVE when the agent reports RUNNING,
   * STOPPING→STOPPED when it reports the tunnel down.
   */
  @PostMapping("/heartbeat")
  public Map<String, Object> heartbeat(@AuthenticationPrincipal Principal principal,
                                       @RequestBody(required = false) HeartbeatRequest req) {
    AgentPrincipal agentP = (AgentPrincipal) principal;
    // Shared with the gRPC channel (§4): identical liveness + reconciliation
    // semantics regardless of transport.
    List<com.duox.dtunnel.application.AgentChannelService.TunnelReport> reports =
        req == null || req.tunnels() == null ? null
            : req.tunnels().stream()
                .map(r -> new com.duox.dtunnel.application.AgentChannelService.TunnelReport(r.tunnelId(), r.status()))
                .toList();
    int desiredVersion = channel.heartbeat(agentP.agentId(),
        req == null ? null : req.appliedVersion(),
        req == null ? null : req.agentVersion(),
        reports);
    return Map.of("desiredVersion", desiredVersion, "serverTime", Instant.now().toString());
  }
}
