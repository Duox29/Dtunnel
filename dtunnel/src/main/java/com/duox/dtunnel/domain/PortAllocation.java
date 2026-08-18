package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "port_allocations")
public class PortAllocation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "port_id", nullable = false)
  private UUID portId;

  @Column(name = "request_id")
  private UUID requestId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "allocated_at", nullable = false, updatable = false)
  private Instant allocatedAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "grace_expires_at")
  private Instant graceExpiresAt;

  public UUID getId() { return id; }
  public UUID getPortId() { return portId; }
  public void setPortId(UUID portId) { this.portId = portId; }
  public UUID getRequestId() { return requestId; }
  public void setRequestId(UUID requestId) { this.requestId = requestId; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public Instant getAllocatedAt() { return allocatedAt; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getGraceExpiresAt() { return graceExpiresAt; }
  public void setGraceExpiresAt(Instant graceExpiresAt) { this.graceExpiresAt = graceExpiresAt; }
}
