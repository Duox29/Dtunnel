package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.*;
import com.duox.dtunnel.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class TunnelService {

  private final TunnelRepository tunnels;
  private final PortAllocationRepository allocations;
  private final PortRepository ports;
  private final AgentRepository agents;
  private final DesiredStateService desiredState;
  private final AuditService audit;

  public TunnelService(TunnelRepository tunnels, PortAllocationRepository allocations,
                       PortRepository ports, AgentRepository agents,
                       DesiredStateService desiredState, AuditService audit) {
    this.tunnels = tunnels;
    this.allocations = allocations;
    this.ports = ports;
    this.agents = agents;
    this.desiredState = desiredState;
    this.audit = audit;
  }

  @Transactional
  public Tunnel create(UUID userId, UUID allocationId, UUID agentId, String name,
                       String targetHost, int targetPort, Integer bandwidthLimitMbps, Integer maxConnections) {
    PortAllocation alloc = allocations.findById(allocationId)
        .orElseThrow(() -> ApiException.notFound("allocation"));
    if (!alloc.getUserId().equals(userId)) throw ApiException.forbidden("allocation belongs to another user");
    if (alloc.getExpiresAt().isBefore(Instant.now())) throw ApiException.conflict("allocation expired");

    Agent agent = agents.findById(agentId).orElseThrow(() -> ApiException.notFound("agent"));
    if (!agent.getUserId().equals(userId)) throw ApiException.forbidden("agent belongs to another user");
    if (agent.getStatus() == AgentStatus.REVOKED) throw ApiException.conflict("agent is revoked");

    if (targetPort < 1 || targetPort > 65535) throw ApiException.badRequest("target_port must be 1..65535");

    Tunnel t = new Tunnel();
    t.setUserId(userId);
    t.setAgentId(agentId);
    t.setPortAllocationId(allocationId);
    t.setName(name);
    t.setTargetHost(targetHost);
    t.setTargetPort(targetPort);
    t.setBandwidthLimitMbps(bandwidthLimitMbps);
    t.setMaxConnections(maxConnections);
    t.setStatus(TunnelStatus.CONFIGURED);
    tunnels.save(t);

    int version = desiredState.publish(agentId);
    audit.log(userId.toString(), "USER", "tunnel.create", "tunnel", t.getId().toString(), "SUCCESS",
        java.util.Map.of("configVersion", version));
    return t;
  }

  @Transactional
  public Tunnel start(UUID userId, UUID tunnelId) {
    Tunnel t = owned(userId, tunnelId);
    if (!EnumSet.of(TunnelStatus.CONFIGURED, TunnelStatus.STOPPED, TunnelStatus.ERROR).contains(t.getStatus())) {
      throw ApiException.conflict("tunnel is " + t.getStatus() + ", cannot start");
    }
    t.setStatus(TunnelStatus.STARTING);
    tunnels.save(t);
    int version = desiredState.publish(t.getAgentId());
    audit.log(userId.toString(), "USER", "tunnel.start", "tunnel", tunnelId.toString(), "SUCCESS",
        java.util.Map.of("configVersion", version));
    return t;
  }

  @Transactional
  public Tunnel stop(UUID userId, UUID tunnelId) {
    Tunnel t = owned(userId, tunnelId);
    if (!EnumSet.of(TunnelStatus.STARTING, TunnelStatus.ACTIVE).contains(t.getStatus())) {
      throw ApiException.conflict("tunnel is " + t.getStatus() + ", cannot stop");
    }
    t.setStatus(TunnelStatus.STOPPING);
    tunnels.save(t);
    int version = desiredState.publish(t.getAgentId());
    audit.log(userId.toString(), "USER", "tunnel.stop", "tunnel", tunnelId.toString(), "SUCCESS",
        java.util.Map.of("configVersion", version));
    return t;
  }

  @Transactional
  public void delete(UUID userId, UUID tunnelId) {
    Tunnel t = owned(userId, tunnelId);
    if (EnumSet.of(TunnelStatus.ACTIVE, TunnelStatus.STARTING).contains(t.getStatus())) {
      throw ApiException.conflict("stop the tunnel before deleting");
    }
    tunnels.delete(t);
    int version = desiredState.publish(t.getAgentId());
    audit.log(userId.toString(), "USER", "tunnel.delete", "tunnel", tunnelId.toString(), "SUCCESS",
        java.util.Map.of("configVersion", version));
  }

  public List<Tunnel> visibleTo(UUID userId, boolean superadmin) {
    return superadmin ? tunnels.findAll() : tunnels.findByUserId(userId);
  }

  public Tunnel ownedFor(UUID userId, UUID tunnelId) {
    return owned(userId, tunnelId);
  }

  private Tunnel owned(UUID userId, UUID tunnelId) {
    Tunnel t = tunnels.findById(tunnelId).orElseThrow(() -> ApiException.notFound("tunnel"));
    if (!t.getUserId().equals(userId)) throw ApiException.forbidden("tunnel belongs to another user");
    return t;
  }
}
