package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "ports")
public class Port {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "node_id", nullable = false)
  private UUID nodeId;

  @Column(nullable = false)
  private String protocol;

  @Column(name = "port_number", nullable = false)
  private int portNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PortStatus status = PortStatus.AVAILABLE;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(name = "reserved_range_id")
  private UUID reservedRangeId;

  public UUID getId() { return id; }
  public UUID getNodeId() { return nodeId; }
  public void setNodeId(UUID nodeId) { this.nodeId = nodeId; }
  public String getProtocol() { return protocol; }
  public void setProtocol(String protocol) { this.protocol = protocol; }
  public int getPortNumber() { return portNumber; }
  public void setPortNumber(int portNumber) { this.portNumber = portNumber; }
  public PortStatus getStatus() { return status; }
  public void setStatus(PortStatus status) { this.status = status; }
  public UUID getOwnerUserId() { return ownerUserId; }
  public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
  public UUID getReservedRangeId() { return reservedRangeId; }
  public void setReservedRangeId(UUID reservedRangeId) { this.reservedRangeId = reservedRangeId; }
}
