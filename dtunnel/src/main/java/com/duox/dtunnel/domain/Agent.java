package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agents")
public class Agent {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "public_key", nullable = false, unique = true)
  private String publicKey;

  @Column(nullable = false)
  private String platform;

  @Column(name = "agent_version")
  private String agentVersion;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AgentStatus status = AgentStatus.PENDING;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getPublicKey() { return publicKey; }
  public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
  public String getPlatform() { return platform; }
  public void setPlatform(String platform) { this.platform = platform; }
  public String getAgentVersion() { return agentVersion; }
  public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }
  public Instant getLastSeenAt() { return lastSeenAt; }
  public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
  public AgentStatus getStatus() { return status; }
  public void setStatus(AgentStatus status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
}
