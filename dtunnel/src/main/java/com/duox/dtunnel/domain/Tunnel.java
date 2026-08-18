package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tunnels")
public class Tunnel {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "agent_id", nullable = false)
  private UUID agentId;

  @Column(name = "port_allocation_id", nullable = false)
  private UUID portAllocationId;

  @Column(nullable = false)
  private String name;

  @Column(name = "target_host", nullable = false)
  private String targetHost;

  @Column(name = "target_port", nullable = false)
  private int targetPort;

  @Column(name = "bandwidth_limit_mbps")
  private Integer bandwidthLimitMbps;

  @Column(name = "max_connections")
  private Integer maxConnections;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TunnelStatus status = TunnelStatus.CREATED;

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public UUID getAgentId() { return agentId; }
  public void setAgentId(UUID agentId) { this.agentId = agentId; }
  public UUID getPortAllocationId() { return portAllocationId; }
  public void setPortAllocationId(UUID portAllocationId) { this.portAllocationId = portAllocationId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getTargetHost() { return targetHost; }
  public void setTargetHost(String targetHost) { this.targetHost = targetHost; }
  public int getTargetPort() { return targetPort; }
  public void setTargetPort(int targetPort) { this.targetPort = targetPort; }
  public Integer getBandwidthLimitMbps() { return bandwidthLimitMbps; }
  public void setBandwidthLimitMbps(Integer bandwidthLimitMbps) { this.bandwidthLimitMbps = bandwidthLimitMbps; }
  public Integer getMaxConnections() { return maxConnections; }
  public void setMaxConnections(Integer maxConnections) { this.maxConnections = maxConnections; }
  public TunnelStatus getStatus() { return status; }
  public void setStatus(TunnelStatus status) { this.status = status; }
}
