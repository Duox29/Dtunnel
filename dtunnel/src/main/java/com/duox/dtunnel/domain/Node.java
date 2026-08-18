package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "nodes")
public class Node {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String region;

  @Column(name = "public_address", nullable = false)
  private String publicAddress;

  /** Base URL of this node's frps admin API (usage metering, Milestone 3.3). */
  @Column(name = "frps_admin_url")
  private String frpsAdminUrl;

  /** Shared secret the Node Agent presents on heartbeat (§1/§3.4). */
  @Column(name = "node_token")
  private String nodeToken;

  /** Last Node Agent heartbeat; stale nodes go OFFLINE (§10). */
  @Column(name = "last_seen_at")
  private java.time.Instant lastSeenAt;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "protocol_capabilities", nullable = false)
  private List<String> protocolCapabilities = List.of("TCP", "UDP");

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "capacity_json")
  private Map<String, Object> capacityJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NodeStatus status = NodeStatus.OFFLINE;

  public UUID getId() { return id; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public String getPublicAddress() { return publicAddress; }
  public void setPublicAddress(String publicAddress) { this.publicAddress = publicAddress; }
  public String getFrpsAdminUrl() { return frpsAdminUrl; }
  public void setFrpsAdminUrl(String frpsAdminUrl) { this.frpsAdminUrl = frpsAdminUrl; }
  public List<String> getProtocolCapabilities() { return protocolCapabilities; }
  public void setProtocolCapabilities(List<String> protocolCapabilities) { this.protocolCapabilities = protocolCapabilities; }
  public Map<String, Object> getCapacityJson() { return capacityJson; }
  public void setCapacityJson(Map<String, Object> capacityJson) { this.capacityJson = capacityJson; }
  public NodeStatus getStatus() { return status; }
  public void setStatus(NodeStatus status) { this.status = status; }
  public String getNodeToken() { return nodeToken; }
  public void setNodeToken(String nodeToken) { this.nodeToken = nodeToken; }
  public java.time.Instant getLastSeenAt() { return lastSeenAt; }
  public void setLastSeenAt(java.time.Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
