package com.duox.dtunnel.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "google_subject", unique = true)
  private String googleSubject;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role = Role.USER;

  @Column(nullable = false)
  private String plan = "FREE";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  public UUID getId() { return id; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getGoogleSubject() { return googleSubject; }
  public void setGoogleSubject(String googleSubject) { this.googleSubject = googleSubject; }
  public Role getRole() { return role; }
  public void setRole(Role role) { this.role = role; }
  public String getPlan() { return plan; }
  public void setPlan(String plan) { this.plan = plan; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
  public void setEmailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
}
