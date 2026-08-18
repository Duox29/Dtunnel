package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "audits")
public class Audit {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String actor;

  @Column(name = "actor_type", nullable = false)
  private String actorType;

  @Column(nullable = false)
  private String action;

  @Column(name = "resource_type")
  private String resourceType;

  @Column(name = "resource_id")
  private String resourceId;

  @Column(nullable = false)
  private String result;

  @Column(name = "source_ip")
  private String sourceIp;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> metadata;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Long getId() { return id; }
  public String getActor() { return actor; }
  public void setActor(String actor) { this.actor = actor; }
  public String getActorType() { return actorType; }
  public void setActorType(String actorType) { this.actorType = actorType; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getResourceType() { return resourceType; }
  public void setResourceType(String resourceType) { this.resourceType = resourceType; }
  public String getResourceId() { return resourceId; }
  public void setResourceId(String resourceId) { this.resourceId = resourceId; }
  public String getResult() { return result; }
  public void setResult(String result) { this.result = result; }
  public String getSourceIp() { return sourceIp; }
  public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
  public Map<String, Object> getMetadata() { return metadata; }
  public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
  public Instant getCreatedAt() { return createdAt; }
}
