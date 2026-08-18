package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.*;
import com.duox.dtunnel.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * detail.md §2 state machine: DRAFT→SUBMITTED→PENDING→APPROVED/REJECTED→ALLOCATED.
 * MVP collapses SUBMITTED/PENDING: a submitted request is immediately PENDING
 * for a SUPERADMIN decision; approval performs the allocation transactionally.
 */
@Service
public class ResourceRequestService {

  private final ResourceRequestRepository requests;
  private final NodeRepository nodes;
  private final PortAllocationService allocationService;
  private final AuditService audit;

  public ResourceRequestService(ResourceRequestRepository requests, NodeRepository nodes,
                                PortAllocationService allocationService, AuditService audit) {
    this.requests = requests;
    this.nodes = nodes;
    this.allocationService = allocationService;
    this.audit = audit;
  }

  @Transactional
  public ResourceRequest submit(UUID userId, UUID nodeId, String protocol,
                                Integer preferredPort, int durationDays, String purpose) {
    Node node = nodes.findById(nodeId).orElseThrow(() -> ApiException.notFound("node"));
    if (!node.getProtocolCapabilities().contains(protocol.toUpperCase())) {
      throw ApiException.badRequest("node " + node.getCode() + " does not support " + protocol);
    }
    if (durationDays < 1 || durationDays > 365) throw ApiException.badRequest("duration_days must be 1..365");

    ResourceRequest r = new ResourceRequest();
    r.setUserId(userId);
    r.setNodeId(nodeId);
    r.setProtocol(protocol.toUpperCase());
    r.setPreferredPort(preferredPort);
    r.setDurationDays(durationDays);
    r.setPurpose(purpose);
    r.setStatus(RequestStatus.PENDING);
    requests.save(r);
    audit.log(userId.toString(), "USER", "request.submit", "resource_request", r.getId().toString(), "SUCCESS", null);
    return r;
  }

  @Transactional
  public PortAllocation approve(UUID adminId, UUID requestId) {
    ResourceRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("request"));
    if (r.getStatus() != RequestStatus.PENDING) {
      throw ApiException.conflict("request is " + r.getStatus() + ", not PENDING");
    }
    r.setStatus(RequestStatus.APPROVED);
    r.setReviewedBy(adminId);
    requests.save(r);

    PortAllocation alloc = allocationService.allocate(adminId.toString(), r.getUserId(), r.getNodeId(),
        r.getProtocol(), r.getPreferredPort(), r.getDurationDays(), r.getId());

    r.setStatus(RequestStatus.ALLOCATED);
    requests.save(r);
    audit.log(adminId.toString(), "ADMIN", "request.approve", "resource_request", r.getId().toString(), "SUCCESS", null);
    return alloc;
  }

  @Transactional
  public ResourceRequest reject(UUID adminId, UUID requestId, String reason) {
    ResourceRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("request"));
    if (r.getStatus() != RequestStatus.PENDING) {
      throw ApiException.conflict("request is " + r.getStatus() + ", not PENDING");
    }
    r.setStatus(RequestStatus.REJECTED);
    r.setReviewedBy(adminId);
    requests.save(r);
    audit.log(adminId.toString(), "ADMIN", "request.reject", "resource_request", r.getId().toString(), "SUCCESS",
        reason == null ? null : java.util.Map.of("reason", reason));
    return r;
  }

  public List<ResourceRequest> visibleTo(UUID userId, boolean superadmin) {
    return superadmin ? requests.findAllByOrderByCreatedAtDesc()
                      : requests.findByUserIdOrderByCreatedAtDesc(userId);
  }
}
