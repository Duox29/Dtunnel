package com.duox.dtunnel.application;

import java.util.UUID;

/**
 * detail.md §4 Phase 2: domain events the gRPC push adapter listens to.
 * Fired synchronously inside the publishing transaction's commit boundary;
 * the push itself is best-effort (REST polling remains the backstop).
 */
public final class AgentEvents {
  private AgentEvents() {}

  /** A new desired-state version was published for an agent. */
  public record DesiredStatePublished(UUID agentId, int version) {}

  /** An agent was revoked — push immediately for sub-second propagation. */
  public record AgentRevoked(UUID agentId) {}
}
