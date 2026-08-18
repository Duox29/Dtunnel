package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.Agent;
import com.duox.dtunnel.domain.AgentStatus;
import com.duox.dtunnel.domain.Tunnel;
import com.duox.dtunnel.domain.TunnelStatus;
import com.duox.dtunnel.repo.AgentRepository;
import com.duox.dtunnel.repo.TunnelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * detail.md §4/§8/§11: heartbeat processing shared by the REST and gRPC agent
 * channels. Keeping it in one place means both transports apply identical
 * liveness + reconciliation semantics.
 */
@Service
public class AgentChannelService {

  public record TunnelReport(String tunnelId, String status) {}

  private final AgentRepository agents;
  private final TunnelRepository tunnels;
  private final DesiredStateService desiredState;
  private final AuditService audit;

  public AgentChannelService(AgentRepository agents, TunnelRepository tunnels,
                             DesiredStateService desiredState, AuditService audit) {
    this.agents = agents;
    this.tunnels = tunnels;
    this.desiredState = desiredState;
    this.audit = audit;
  }

  /**
   * Apply one heartbeat: bump liveness, restore OFFLINE→ONLINE (a valid device
   * token proves the approved device is alive; §10 stale detection is
   * liveness-only), and reconcile reported tunnel states. Returns the current
   * desired-state version so the caller can tell the agent to re-sync.
   */
  @Transactional
  public int heartbeat(UUID agentId, Integer appliedVersion, String agentVersion, List<TunnelReport> reports) {
    Agent a = agents.findById(agentId).orElseThrow(() -> ApiException.notFound("agent"));
    a.setLastSeenAt(Instant.now());
    if (a.getStatus() == AgentStatus.OFFLINE) a.setStatus(AgentStatus.ONLINE);
    if (agentVersion != null) a.setAgentVersion(agentVersion);
    agents.save(a);

    if (reports != null) {
      for (TunnelReport r : reports) {
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
      }
    }
    return desiredState.currentVersion(agentId);
  }
}
