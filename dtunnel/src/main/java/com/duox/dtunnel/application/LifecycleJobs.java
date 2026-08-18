package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.*;
import com.duox.dtunnel.repo.*;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * detail.md §10 background jobs. Each transition is checked, not blindly
 * applied, so reruns are idempotent. Expiration rule (§2): 5-day warning →
 * expiry → service stopped → 3-day grace hold → release.
 */
@Component
public class LifecycleJobs {

  private static final Logger log = LoggerFactory.getLogger(LifecycleJobs.class);
  private static final Duration GRACE_PERIOD = Duration.ofDays(3);

  private final PortAllocationRepository allocations;
  private final PortRepository ports;
  private final TunnelRepository tunnels;
  private final AgentRepository agents;
  private final DesiredStateService desiredState;
  private final AuditService audit;
  private final Duration staleThreshold;

  public LifecycleJobs(PortAllocationRepository allocations, PortRepository ports,
                       TunnelRepository tunnels, AgentRepository agents,
                       DesiredStateService desiredState, AuditService audit,
                       @Value("${dtunnel.agent.stale-threshold:PT60S}") Duration staleThreshold) {
    this.allocations = allocations;
    this.ports = ports;
    this.tunnels = tunnels;
    this.agents = agents;
    this.desiredState = desiredState;
    this.audit = audit;
    this.staleThreshold = staleThreshold;
  }

  /** 5-day warning window → mark tunnels EXPIRING (feeds UI warnings). */
  @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
  @SchedulerLock(name = "processExpirationWarnings", lockAtLeastFor = "PT30S")
  @Transactional
  public void processExpirationWarnings() {
    Instant now = Instant.now();
    for (PortAllocation pa : allocations.findExpiring(now, now.plus(5, ChronoUnit.DAYS))) {
      for (Tunnel t : tunnels.findByPortAllocationId(pa.getId())) {
        if (t.getStatus() == TunnelStatus.ACTIVE || t.getStatus() == TunnelStatus.STARTING) {
          t.setStatus(TunnelStatus.EXPIRING);
          tunnels.save(t);
          audit.log("system", "SYSTEM", "tunnel.expiring_warning", "tunnel", t.getId().toString(), "SUCCESS", null);
        }
      }
    }
  }

  /** Expiry reached → stop service, start 3-day grace hold on the port. */
  @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
  @SchedulerLock(name = "processExpirations", lockAtLeastFor = "PT30S")
  @Transactional
  public void processExpirations() {
    for (PortAllocation pa : allocations.findExpiredNoGrace(Instant.now())) {
      pa.setGraceExpiresAt(Instant.now().plus(GRACE_PERIOD));
      allocations.save(pa);
      for (Tunnel t : tunnels.findByPortAllocationId(pa.getId())) {
        if (EnumSet.of(TunnelStatus.ACTIVE, TunnelStatus.STARTING, TunnelStatus.CONFIGURED, TunnelStatus.EXPIRING)
            .contains(t.getStatus())) {
          t.setStatus(TunnelStatus.STOPPING);
          tunnels.save(t);
          desiredState.publish(t.getAgentId());
        }
      }
      ports.findById(pa.getPortId()).ifPresent(p -> {
        if (p.getStatus() == PortStatus.ACTIVE || p.getStatus() == PortStatus.ALLOCATED) {
          p.setStatus(PortStatus.EXPIRED_PENDING_RELEASE);
          ports.save(p);
        }
      });
      audit.log("system", "SYSTEM", "allocation.expired", "port_allocation", pa.getId().toString(), "SUCCESS", null);
      log.info("allocation {} expired; grace until {}", pa.getId(), pa.getGraceExpiresAt());
    }
  }

  /** Grace elapsed → release the port back to the pool (re-allocatable). */
  @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
  @SchedulerLock(name = "processGraceReleases", lockAtLeastFor = "PT30S")
  @Transactional
  public void processGraceReleases() {
    for (PortAllocation pa : allocations.findGraceElapsed(Instant.now())) {
      ports.findById(pa.getPortId()).ifPresent(p -> {
        p.setStatus(PortStatus.RELEASED);
        p.setOwnerUserId(null);
        ports.save(p);
      });
      for (Tunnel t : tunnels.findByPortAllocationId(pa.getId())) {
        if (t.getStatus() != TunnelStatus.EXPIRED && t.getStatus() != TunnelStatus.STOPPED) {
          t.setStatus(TunnelStatus.EXPIRED);
          tunnels.save(t);
        }
      }
      audit.log("system", "SYSTEM", "port.released", "port_allocation", pa.getId().toString(), "SUCCESS", null);
      log.info("allocation {} grace elapsed; port released", pa.getId());
    }
  }

  /** detail.md §10: no heartbeat/Ping within threshold → OFFLINE. */
  @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
  @SchedulerLock(name = "detectStaleAgents", lockAtLeastFor = "PT10S")
  @Transactional
  public void detectStaleAgents() {
    Instant cutoff = Instant.now().minus(staleThreshold);
    List<Agent> stale = agents.findStaleOnline(cutoff);
    for (Agent a : stale) {
      a.setStatus(AgentStatus.OFFLINE);
      agents.save(a);
      audit.log(a.getId().toString(), "SYSTEM", "agent.offline", "agent", a.getId().toString(), "SUCCESS", null);
      log.info("agent {} marked OFFLINE (last seen {})", a.getId(), a.getLastSeenAt());
    }
  }

  /**
   * detail.md §11 reconciliation: desired ACTIVE but observed down for too
   * long → flag ERROR so the next agent poll can restart it.
   */
  @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
  @SchedulerLock(name = "reconcileDesiredVsObserved", lockAtLeastFor = "PT15S")
  @Transactional
  public void reconcileDesiredVsObserved() {
    // STARTING tunnels whose agent went silent get flagged; the agent's next
    // heartbeat/config poll drives the actual restart.
    java.util.Set<UUID> errorPublish = new java.util.HashSet<>();
    for (Tunnel t : tunnels.findByStatus(TunnelStatus.STARTING)) {
      Agent a = agents.findById(t.getAgentId()).orElse(null);
      if (a == null || a.getStatus() == AgentStatus.OFFLINE || a.getStatus() == AgentStatus.REVOKED) {
        t.setStatus(TunnelStatus.ERROR);
        tunnels.save(t);
        errorPublish.add(t.getAgentId());
        audit.log("system", "SYSTEM", "tunnel.reconcile_error", "tunnel", t.getId().toString(), "SUCCESS", null);
      }
    }
    for (UUID agentId : errorPublish) {
      desiredState.publish(agentId); // ERROR leaves the desired set → bump version
    }

    // detail.md §11 recovery: ERROR tunnels whose agent is back ONLINE are
    // re-armed (ERROR → STARTING) and re-published so the agent restarts them.
    // ERROR is only ever reached via an unexpected close/flag, never by an
    // explicit user stop (that path is STOPPING → STOPPED), so recovery is safe.
    java.util.Set<UUID> republish = new java.util.HashSet<>();
    for (Tunnel t : tunnels.findByStatus(TunnelStatus.ERROR)) {
      Agent a = agents.findById(t.getAgentId()).orElse(null);
      if (a != null && a.getStatus() == AgentStatus.ONLINE) {
        t.setStatus(TunnelStatus.STARTING);
        tunnels.save(t);
        republish.add(t.getAgentId());
        audit.log("system", "SYSTEM", "tunnel.reconcile_recover", "tunnel", t.getId().toString(), "SUCCESS", null);
        log.info("tunnel {} recovered from ERROR (agent {} back online)", t.getId(), a.getId());
      }
    }
    for (UUID agentId : republish) {
      desiredState.publish(agentId);
    }
  }
}
