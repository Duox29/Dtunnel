package com.duox.dtunnel.api;

import com.duox.dtunnel.application.ResourceRequestService;
import com.duox.dtunnel.domain.PortAllocation;
import com.duox.dtunnel.domain.ResourceRequest;
import com.duox.dtunnel.domain.User;
import com.duox.dtunnel.repo.ResourceRequestRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** detail.md §8 + Milestone 1.3: submit → SUPERADMIN approve/reject → allocation. */
@RestController
@RequestMapping("/api/v1/resource-requests")
public class ResourceRequestController {

  public record CreateRequest(@NotBlank String nodeId, @NotBlank String protocol,
                              Integer preferredPort,
                              @Min(1) @Max(365) int durationDays, String purpose) {}
  public record RejectRequest(String reason) {}

  private final ResourceRequestService service;
  private final ResourceRequestRepository requests;
  private final CurrentUser currentUser;

  public ResourceRequestController(ResourceRequestService service, ResourceRequestRepository requests,
                                   CurrentUser currentUser) {
    this.service = service;
    this.requests = requests;
    this.currentUser = currentUser;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@jakarta.validation.Valid @RequestBody CreateRequest req) {
    User u = currentUser.require();
    ResourceRequest r = service.submit(u.getId(), UUID.fromString(req.nodeId()), req.protocol(),
        req.preferredPort(), req.durationDays(), req.purpose());
    return json(r);
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    User u = currentUser.require();
    return service.visibleTo(u.getId(), currentUser.isSuperadmin()).stream()
        .map(ResourceRequestController::json).toList();
  }

  @PostMapping("/{id}/approve")
  public Map<String, Object> approve(@PathVariable UUID id) {
    User admin = currentUser.requireSuperadmin();
    PortAllocation alloc = service.approve(admin.getId(), id);
    return Map.of("status", "ALLOCATED", "allocationId", alloc.getId().toString(),
        "expiresAt", alloc.getExpiresAt().toString());
  }

  @PostMapping("/{id}/reject")
  public Map<String, Object> reject(@PathVariable UUID id, @RequestBody(required = false) RejectRequest req) {
    User admin = currentUser.requireSuperadmin();
    ResourceRequest r = service.reject(admin.getId(), id, req == null ? null : req.reason());
    return json(r);
  }

  static Map<String, Object> json(ResourceRequest r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId().toString());
    m.put("userId", r.getUserId().toString());
    m.put("nodeId", r.getNodeId().toString());
    m.put("protocol", r.getProtocol());
    m.put("preferredPort", r.getPreferredPort());
    m.put("durationDays", r.getDurationDays());
    m.put("purpose", r.getPurpose());
    m.put("status", r.getStatus().name());
    m.put("createdAt", r.getCreatedAt().toString());
    return m;
  }
}
