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
  private final NodeRepository nodes;
  private final DesiredStateService desiredState;
  private final AuditService audit;

  public TunnelService(TunnelRepository tunnels, PortAllocationRepository allocations,
                       PortRepository ports, AgentRepository agents, NodeRepository nodes,
                       DesiredStateService desiredState, AuditService audit) {
    this.tunnels = tunnels;
    this.allocations = allocations;
    this.ports = ports;
    this.agents = agents;
    this.nodes = nodes;
    this.desiredState = desiredState;
    this.audit = audit;
  }

  /** detail.md Milestone 1.5: PORT tunnel (tcp/udp) from an owned allocation. */
  @Transactional
  public Tunnel create(UUID userId, UUID allocationId, UUID agentId, String name,
                       String targetHost, int targetPort, Integer bandwidthLimitMbps, Integer maxConnections) {
    PortAllocation alloc = allocations.findById(allocationId)
        .orElseThrow(() -> ApiException.notFound("allocation"));
    if (!alloc.getUserId().equals(userId)) throw ApiException.forbidden("allocation belongs to another user");
    if (alloc.getExpiresAt().isBefore(Instant.now())) throw ApiException.conflict("allocation expired");

    Agent agent = checkAgent(userId, agentId);
    if (targetPort < 1 || targetPort > 65535) throw ApiException.badRequest("target_port must be 1..65535");

    Tunnel t = new Tunnel();
    t.setUserId(userId);
    t.setAgentId(agentId);
    t.setPortAllocationId(allocationId);
    t.setNodeId(nodeIdOfAllocation(alloc));
    t.setName(name);
    t.setTargetHost(targetHost);
    t.setTargetPort(targetPort);
    t.setBandwidthLimitMbps(bandwidthLimitMbps);
    t.setMaxConnections(maxConnections);
    t.setTunnelType("PORT");
    t.setStatus(TunnelStatus.CONFIGURED);
    tunnels.save(t);

    int version = desiredState.publish(agentId);
    audit.log(userId.toString(), "USER", "tunnel.create", "tunnel", t.getId().toString(), "SUCCESS",
        java.util.Map.of("configVersion", version, "type", "PORT"));
    return t;
  }

  /**
   * detail.md §3.6: HTTP tunnel — domain-routed over the node's shared frps
   * vhost HTTP port (no dedicated port allocation). The domain must be unique
   * platform-wide (uq_tunnel_domain) and the node must expose a vhost port.
   */
  @Transactional
  public Tunnel createHttp(UUID userId, UUID nodeId, UUID agentId, String name,
                           String domain, String targetHost, int targetPort,
                           Integer bandwidthLimitMbps) {
    Node node = nodes.findById(nodeId).orElseThrow(() -> ApiException.notFound("node"));
    if (node.getVhostHttpPort() == null) {
      throw ApiException.conflict("node " + node.getCode() + " has no HTTP vhost port configured");
    }
    checkAgent(userId, agentId);
    String d = normalizeDomain(domain);
    if (tunnels.findByDomain(d).isPresent()) throw ApiException.conflict("domain already in use: " + d);
    if (targetPort < 1 || targetPort > 65535) throw ApiException.badRequest("target_port must be 1..65535");

    Tunnel t = new Tunnel();
    t.setUserId(userId);
    t.setAgentId(agentId);
    t.setNodeId(nodeId);
    t.setDomain(d);
    t.setName(name);
    t.setTargetHost(targetHost);
    t.setTargetPort(targetPort);
    t.setBandwidthLimitMbps(bandwidthLimitMbps);
    t.setTunnelType("HTTP");
    t.setStatus(TunnelStatus.CONFIGURED);
    tunnels.save(t);

    int version = desiredState.publish(agentId);
    audit.log(userId.toString(), "USER", "tunnel.create", "tunnel", t.getId().toString(), "SUCCESS",
        java.util.Map.of("configVersion", version, "type", "HTTP", "domain", d));
    return t;
  }

  private Agent checkAgent(UUID userId, UUID agentId) {
    Agent agent = agents.findById(agentId).orElseThrow(() -> ApiException.notFound("agent"));
    if (!agent.getUserId().equals(userId)) throw ApiException.forbidden("agent belongs to another user");
    if (agent.getStatus() == AgentStatus.REVOKED) throw ApiException.conflict("agent is revoked");
    return agent;
  }

  private UUID nodeIdOfAllocation(PortAllocation alloc) {
    return ports.findById(alloc.getPortId()).map(Port::getNodeId).orElse(null);
  }

  private static final java.util.regex.Pattern DOMAIN =
      java.util.regex.Pattern.compile("^(?=.{1,253}$)[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$");

  private String normalizeDomain(String domain) {
    if (domain == null || domain.isBlank()) throw ApiException.badRequest("domain is required");
    String d = domain.trim().toLowerCase();
    if (!DOMAIN.matcher(d).matches()) throw ApiException.badRequest("invalid domain: " + domain);
    return d;
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
