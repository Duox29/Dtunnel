package com.duox.dtunnel.security;

import com.duox.dtunnel.domain.Agent;

import java.security.Principal;
import java.util.UUID;

/** Authenticated agent identity resolved from the device session token. */
public record AgentPrincipal(UUID agentId, UUID userId, String publicKey) implements Principal {
  public static AgentPrincipal of(Agent agent) {
    return new AgentPrincipal(agent.getId(), agent.getUserId(), agent.getPublicKey());
  }
  @Override
  public String getName() {
    return "agent:" + agentId;
  }
}
