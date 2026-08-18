package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resource_requests")
public class ResourceRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "node_id", nullable = false)
  private UUID nodeId;

  @Column(nullable = false)
  private String protocol;

  @Column(name = "preferred_port")
  private Integer preferredPort;

  @Column(name = "duration_days", nullable = false)
  private int durationDays = 30;

  private String purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RequestStatus status = RequestStatus.DRAFT;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public UUID getNodeId() { return nodeId; }
  public void setNodeId(UUID nodeId) { this.nodeId = nodeId; }
  public String getProtocol() { return protocol; }
  public void setProtocol(String protocol) { this.protocol = protocol; }
  public Integer getPreferredPort() { return preferredPort; }
  public void setPreferredPort(Integer preferredPort) { this.preferredPort = preferredPort; }
  public int getDurationDays() { return durationDays; }
  public void setDurationDays(int durationDays) { this.durationDays = durationDays; }
  public String getPurpose() { return purpose; }
  public void setPurpose(String purpose) { this.purpose = purpose; }
  public RequestStatus getStatus() { return status; }
  public void setStatus(RequestStatus status) { this.status = status; }
  public UUID getReviewedBy() { return reviewedBy; }
  public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
  public Instant getCreatedAt() { return createdAt; }
}
