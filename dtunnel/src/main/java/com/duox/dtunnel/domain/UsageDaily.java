package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** detail.md §10 aggregateUsage(): daily rollup of usage_records. */
@Entity
@Table(name = "usage_daily")
@IdClass(UsageDaily.Key.class)
public class UsageDaily {

  @Id
  @Column(name = "tunnel_id", nullable = false)
  private UUID tunnelId;

  @Id
  @Column(name = "day", nullable = false)
  private LocalDate day;

  @Column(name = "bytes_in", nullable = false)
  private long bytesIn;

  @Column(name = "bytes_out", nullable = false)
  private long bytesOut;

  public UUID getTunnelId() { return tunnelId; }
  public void setTunnelId(UUID tunnelId) { this.tunnelId = tunnelId; }
  public LocalDate getDay() { return day; }
  public void setDay(LocalDate day) { this.day = day; }
  public long getBytesIn() { return bytesIn; }
  public void setBytesIn(long bytesIn) { this.bytesIn = bytesIn; }
  public long getBytesOut() { return bytesOut; }
  public void setBytesOut(long bytesOut) { this.bytesOut = bytesOut; }

  public static class Key implements Serializable {
    private UUID tunnelId;
    private LocalDate day;

    public Key() {}
    public Key(UUID tunnelId, LocalDate day) { this.tunnelId = tunnelId; this.day = day; }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key k)) return false;
      return Objects.equals(tunnelId, k.tunnelId) && Objects.equals(day, k.day);
    }
    @Override public int hashCode() { return Objects.hash(tunnelId, day); }
  }
}
