package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_records")
public class UsageRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tunnel_id", nullable = false)
  private UUID tunnelId;

  @Column(name = "bytes_in", nullable = false)
  private long bytesIn;

  @Column(name = "bytes_out", nullable = false)
  private long bytesOut;

  @Column(name = "active_seconds", nullable = false)
  private int activeSeconds;

  @Column(name = "bucket_start", nullable = false)
  private Instant bucketStart;

  public Long getId() { return id; }
  public UUID getTunnelId() { return tunnelId; }
  public void setTunnelId(UUID tunnelId) { this.tunnelId = tunnelId; }
  public long getBytesIn() { return bytesIn; }
  public void setBytesIn(long bytesIn) { this.bytesIn = bytesIn; }
  public long getBytesOut() { return bytesOut; }
  public void setBytesOut(long bytesOut) { this.bytesOut = bytesOut; }
  public int getActiveSeconds() { return activeSeconds; }
  public void setActiveSeconds(int activeSeconds) { this.activeSeconds = activeSeconds; }
  public Instant getBucketStart() { return bucketStart; }
  public void setBucketStart(Instant bucketStart) { this.bucketStart = bucketStart; }
}
