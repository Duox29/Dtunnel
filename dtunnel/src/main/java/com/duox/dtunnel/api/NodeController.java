package com.duox.dtunnel.api;

import com.duox.dtunnel.application.NodeService;
import com.duox.dtunnel.domain.Node;
import com.duox.dtunnel.domain.PortStatus;
import com.duox.dtunnel.repo.NodeRepository;
import com.duox.dtunnel.repo.PortRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** detail.md §8: node listing for users; registration + port seeding SUPERADMIN-only (Milestone 1.2). */
@RestController
@RequestMapping("/api/v1/nodes")
public class NodeController {

  public record RegisterNodeRequest(@NotBlank String code, @NotBlank String region,
                                    @NotBlank String publicAddress, List<String> protocolCapabilities,
                                    String frpsAdminUrl, Integer vhostHttpPort) {}
  public record SeedPortsRequest(@NotBlank String protocol,
                                 @Min(1) @Max(65535) int start,
                                 @Min(1) @Max(65535) int end) {}

  private final NodeRepository nodes;
  private final PortRepository ports;
  private final NodeService nodeService;
  private final CurrentUser currentUser;

  public NodeController(NodeRepository nodes, PortRepository ports,
                        NodeService nodeService, CurrentUser currentUser) {
    this.nodes = nodes;
    this.ports = ports;
    this.nodeService = nodeService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    currentUser.require();
    return nodes.findAll().stream().map(NodeController::json).toList();
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable UUID id) {
    currentUser.require();
    Node n = nodes.findById(id).orElseThrow(() -> com.duox.dtunnel.application.ApiException.notFound("node"));
    return json(n);
  }

  @PostMapping
  public Map<String, Object> register(@jakarta.validation.Valid @RequestBody RegisterNodeRequest req) {
    var admin = currentUser.requireSuperadmin();
    Node n = nodeService.registerNode(admin.getEmail(), req.code(), req.region(),
        req.publicAddress(), req.protocolCapabilities(), req.frpsAdminUrl());
    if (req.vhostHttpPort() != null) {
      n.setVhostHttpPort(req.vhostHttpPort());
      nodes.save(n);
    }
    return json(n);
  }

  public record UpdateNodeRequest(String publicAddress, String frpsAdminUrl, Integer vhostHttpPort) {}

  /** SUPERADMIN: update node endpoints (e.g. point usage collection at frps admin API). */
  @PatchMapping("/{id}")
  public Map<String, Object> update(@PathVariable UUID id,
                                    @RequestBody UpdateNodeRequest req) {
    currentUser.requireSuperadmin();
    Node n = nodes.findById(id)
        .orElseThrow(() -> com.duox.dtunnel.application.ApiException.notFound("node"));
    if (req.publicAddress() != null && !req.publicAddress().isBlank()) n.setPublicAddress(req.publicAddress());
    if (req.frpsAdminUrl() != null) n.setFrpsAdminUrl(req.frpsAdminUrl().isBlank() ? null : req.frpsAdminUrl());
    if (req.vhostHttpPort() != null) n.setVhostHttpPort(req.vhostHttpPort() == 0 ? null : req.vhostHttpPort());
    nodes.save(n);
    return json(n);
  }

  /**
   * SUPERADMIN: issue or rotate the Node Agent shared secret (§15 rotation).
   * Also backfills tokens for nodes registered before the Node Agent existed.
   */
  @PostMapping("/{id}/rotate-token")
  public Map<String, Object> rotateToken(@PathVariable UUID id) {
    var admin = currentUser.requireSuperadmin();
    Node n = nodes.findById(id)
        .orElseThrow(() -> com.duox.dtunnel.application.ApiException.notFound("node"));
    n.setNodeToken(java.util.UUID.randomUUID().toString().replace("-", ""));
    nodes.save(n);
    return Map.of("nodeId", n.getId().toString(), "nodeToken", n.getNodeToken());
  }

  @PostMapping("/{id}/ports/seed")
  public Map<String, Object> seedPorts(@PathVariable UUID id,
                                       @jakarta.validation.Valid @RequestBody SeedPortsRequest req) {
    var admin = currentUser.requireSuperadmin();
    int created = nodeService.seedPorts(admin.getEmail(), id, req.protocol().toUpperCase(), req.start(), req.end());
    return Map.of("created", created);
  }

  /** detail.md §8: latency test trigger — UX only in MVP. */
  @PostMapping("/{id}/ping")
  public Map<String, Object> ping(@PathVariable UUID id) {
    currentUser.require();
    Node n = nodes.findById(id).orElseThrow(() -> com.duox.dtunnel.application.ApiException.notFound("node"));
    long start = System.nanoTime();
    try (var socket = new java.net.Socket()) {
      socket.connect(new java.net.InetSocketAddress(n.getPublicAddress(), 7000), 3000);
    } catch (Exception e) {
      return Map.of("reachable", false, "error", String.valueOf(e.getMessage()));
    }
    long ms = (System.nanoTime() - start) / 1_000_000;
    return Map.of("reachable", true, "latencyMs", ms);
  }

  static Map<String, Object> json(Node n) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", n.getId().toString());
    m.put("code", n.getCode());
    m.put("region", n.getRegion());
    m.put("publicAddress", n.getPublicAddress());
    m.put("protocolCapabilities", n.getProtocolCapabilities());
    m.put("frpsAdminUrl", n.getFrpsAdminUrl());
    m.put("vhostHttpPort", n.getVhostHttpPort());
    m.put("status", n.getStatus().name());
    // Node Agent (§1/§3.4): token shown to SUPERADMIN at registration;
    // lastSeen + capacity come from duox-node-agent heartbeats.
    m.put("nodeToken", n.getNodeToken());
    m.put("lastSeenAt", n.getLastSeenAt() == null ? null : n.getLastSeenAt().toString());
    m.put("capacity", n.getCapacityJson());
    return m;
  }
}
