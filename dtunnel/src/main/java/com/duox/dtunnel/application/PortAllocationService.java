package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.*;
import com.duox.dtunnel.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * detail.md §5: SELECT ... FOR UPDATE SKIP LOCKED allocation. The preferred
 * port heads the candidate list; the loser of a race falls through to the
 * next candidate instead of deadlocking.
 */
@Service
public class PortAllocationService {

  private final PortRepository ports;
  private final PortAllocationRepository allocations;
  private final NodeRepository nodes;
  private final AuditService audit;

  public PortAllocationService(PortRepository ports, PortAllocationRepository allocations,
                               NodeRepository nodes, AuditService audit) {
    this.ports = ports;
    this.allocations = allocations;
    this.nodes = nodes;
    this.audit = audit;
  }

  @Transactional
  public PortAllocation allocate(String actor, UUID userId, UUID nodeId, String protocol,
                                 Integer preferredPort, int durationDays, UUID requestId) {
    Node node = nodes.findById(nodeId).orElseThrow(() -> ApiException.notFound("node"));

    List<Integer> candidates = buildCandidates(nodeId, protocol, preferredPort);
    if (candidates.isEmpty()) throw ApiException.conflict("no available ports on node " + node.getCode());

    Port port = ports.lockFirstAvailable(nodeId, protocol, candidates)
        .orElseThrow(() -> ApiException.conflict("no available ports on node " + node.getCode() + " (race lost, retry)"));

    port.setStatus(PortStatus.ALLOCATED);
    port.setOwnerUserId(userId);
    ports.save(port);

    PortAllocation alloc = new PortAllocation();
    alloc.setPortId(port.getId());
    alloc.setRequestId(requestId);
    alloc.setUserId(userId);
    alloc.setExpiresAt(Instant.now().plus(durationDays, ChronoUnit.DAYS));
    allocations.save(alloc);

    audit.log(actor, "ADMIN", "port.allocate", "port", port.getId().toString(), "SUCCESS",
        java.util.Map.of("node", node.getCode(), "protocol", protocol,
            "portNumber", port.getPortNumber(), "userId", userId.toString(), "days", durationDays));
    return alloc;
  }

  private List<Integer> buildCandidates(UUID nodeId, String protocol, Integer preferred) {
    List<Integer> candidates = new ArrayList<>();
    if (preferred != null) candidates.add(preferred);
    // suggestions: up to 20 available ports ascending (v0.1 §8.3 port-suggestion UX)
    for (Port p : ports.findByNodeIdAndStatus(nodeId, PortStatus.AVAILABLE)) {
      if (p.getProtocol().equalsIgnoreCase(protocol) && !candidates.contains(p.getPortNumber())) {
        candidates.add(p.getPortNumber());
        if (candidates.size() >= 21) break;
      }
    }
    return candidates;
  }
}
