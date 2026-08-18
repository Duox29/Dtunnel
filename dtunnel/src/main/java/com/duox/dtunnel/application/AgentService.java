package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.Agent;
import com.duox.dtunnel.domain.AgentStatus;
import com.duox.dtunnel.repo.AgentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * detail.md §6: the Ed25519 public key is the durable device identity.
 * Registration binds it to a user account; a short-lived session token is
 * issued afterwards (AgentTokenService) and never the key itself.
 */
@Service
public class AgentService {

  private final AgentRepository agents;
  private final AuditService audit;
  private final org.springframework.context.ApplicationEventPublisher events;

  public AgentService(AgentRepository agents, AuditService audit,
                      org.springframework.context.ApplicationEventPublisher events) {
    this.agents = agents;
    this.audit = audit;
    this.events = events;
  }

  @Transactional
  public Agent register(UUID userId, String publicKey, String platform, String agentVersion) {
    if (publicKey == null || publicKey.isBlank()) throw ApiException.badRequest("public_key required");
    agents.findByPublicKey(publicKey).ifPresent(existing -> {
      throw ApiException.conflict("device already registered");
    });
    Agent a = new Agent();
    a.setUserId(userId);
    a.setPublicKey(publicKey.trim());
    a.setPlatform(platform == null ? "unknown" : platform.toLowerCase());
    a.setAgentVersion(agentVersion);
    a.setStatus(AgentStatus.PENDING);
    agents.save(a);
    audit.log(userId.toString(), "USER", "agent.register", "agent", a.getId().toString(), "SUCCESS",
        java.util.Map.of("platform", a.getPlatform()));
    return a;
  }

  @Transactional
  public Agent approve(UUID adminId, UUID agentId) {
    Agent a = agents.findById(agentId).orElseThrow(() -> ApiException.notFound("agent"));
    if (a.getStatus() == AgentStatus.REVOKED) throw ApiException.conflict("agent is revoked");
    a.setStatus(AgentStatus.ONLINE);
    a.setLastSeenAt(Instant.now());
    agents.save(a);
    audit.log(adminId.toString(), "ADMIN", "agent.approve", "agent", a.getId().toString(), "SUCCESS", null);
    return a;
  }

  @Transactional
  public Agent revoke(UUID adminId, UUID agentId) {
    Agent a = agents.findById(agentId).orElseThrow(() -> ApiException.notFound("agent"));
    a.setStatus(AgentStatus.REVOKED);
    agents.save(a);
    audit.log(adminId.toString(), "ADMIN", "agent.revoke", "agent", a.getId().toString(), "SUCCESS", null);
    // §4 Phase 2: sub-second revocation — push over the open gRPC stream now;
    // REST agents still catch it on their next poll (bounded by heartbeat).
    events.publishEvent(new AgentEvents.AgentRevoked(agentId));
    return a;
  }
}
