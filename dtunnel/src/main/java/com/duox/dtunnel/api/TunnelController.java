package com.duox.dtunnel.api;

import com.duox.dtunnel.application.TunnelService;
import com.duox.dtunnel.application.UsageService;
import com.duox.dtunnel.domain.Tunnel;
import com.duox.dtunnel.domain.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** detail.md §8: tunnel create/start/stop from owned allocations. */
@RestController
@RequestMapping("/api/v1/tunnels")
public class TunnelController {

  public record CreateTunnelRequest(@NotBlank String allocationId, @NotBlank String agentId,
                                    @NotBlank String name, @NotBlank String targetHost,
                                    @Min(1) @Max(65535) int targetPort,
                                    Integer bandwidthLimitMbps, Integer maxConnections) {}

  private final TunnelService service;
  private final UsageService usageService;
  private final CurrentUser currentUser;

  public TunnelController(TunnelService service, UsageService usageService, CurrentUser currentUser) {
    this.service = service;
    this.usageService = usageService;
    this.currentUser = currentUser;
  }

  /** detail.md Milestone 3.3: per-tunnel usage totals for the owner. */
  @GetMapping("/{id}/usage")
  public Map<String, Object> usage(@PathVariable UUID id) {
    User u = currentUser.require();
    Tunnel t = service.ownedFor(u.getId(), id);
    return Map.of(
        "tunnelId", t.getId().toString(),
        "bytesIn", usageService.bytesIn(id),
        "bytesOut", usageService.bytesOut(id));
  }

  /** detail.md §10 aggregateUsage(): daily usage history for charts. */
  @GetMapping("/{id}/usage/history")
  public Map<String, Object> usageHistory(@PathVariable UUID id,
                                          @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
    User u = currentUser.require();
    Tunnel t = service.ownedFor(u.getId(), id);
    return Map.of(
        "tunnelId", t.getId().toString(),
        "days", usageService.history(id, days));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@jakarta.validation.Valid @RequestBody CreateTunnelRequest req) {
    User u = currentUser.require();
    Tunnel t = service.create(u.getId(), UUID.fromString(req.allocationId()), UUID.fromString(req.agentId()),
        req.name(), req.targetHost(), req.targetPort(), req.bandwidthLimitMbps(), req.maxConnections());
    return json(t);
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    User u = currentUser.require();
    return service.visibleTo(u.getId(), currentUser.isSuperadmin()).stream()
        .map(TunnelController::json).toList();
  }

  @PostMapping("/{id}/start")
  public Map<String, Object> start(@PathVariable UUID id) {
    return json(service.start(currentUser.id(), id));
  }

  @PostMapping("/{id}/stop")
  public Map<String, Object> stop(@PathVariable UUID id) {
    return json(service.stop(currentUser.id(), id));
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable UUID id) {
    service.delete(currentUser.id(), id);
    return Map.of("status", "deleted");
  }

  static Map<String, Object> json(Tunnel t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.getId().toString());
    m.put("name", t.getName());
    m.put("agentId", t.getAgentId().toString());
    m.put("allocationId", t.getPortAllocationId().toString());
    m.put("targetHost", t.getTargetHost());
    m.put("targetPort", t.getTargetPort());
    m.put("bandwidthLimitMbps", t.getBandwidthLimitMbps());
    m.put("maxConnections", t.getMaxConnections());
    m.put("status", t.getStatus().name());
    return m;
  }
}
