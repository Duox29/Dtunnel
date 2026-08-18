package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.Node;
import com.duox.dtunnel.domain.NodeStatus;
import com.duox.dtunnel.domain.Port;
import com.duox.dtunnel.domain.PortStatus;
import com.duox.dtunnel.repo.NodeRepository;
import com.duox.dtunnel.repo.PortRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NodeService {

  private final NodeRepository nodes;
  private final PortRepository ports;
  private final AuditService audit;

  public NodeService(NodeRepository nodes, PortRepository ports, AuditService audit) {
    this.nodes = nodes;
    this.ports = ports;
    this.audit = audit;
  }

  @Transactional
  public Node registerNode(String actor, String code, String region, String publicAddress,
                           List<String> capabilities, String frpsAdminUrl) {
    if (nodes.findByCode(code).isPresent()) throw ApiException.conflict("node code exists: " + code);
    Node n = new Node();
    n.setCode(code);
    n.setRegion(region);
    n.setPublicAddress(publicAddress);
    n.setFrpsAdminUrl(frpsAdminUrl);
    if (capabilities != null && !capabilities.isEmpty()) n.setProtocolCapabilities(capabilities);
    n.setStatus(NodeStatus.ONLINE);
    // Node Agent shared secret (§1/§3.4): generated once, shown to the admin at
    // registration time, and presented by duox-node-agent on every heartbeat.
    n.setNodeToken(java.util.UUID.randomUUID().toString().replace("-", ""));
    nodes.save(n);
    audit.log(actor, "ADMIN", "node.register", "node", n.getId().toString(), "SUCCESS", null);
    return n;
  }

  /**
   * detail.md §1/§3.4: Node Agent heartbeat — authenticates by node_token,
   * records health/capacity metrics into nodes.capacity_json, marks ONLINE.
   */
  @Transactional
  public Node heartbeat(String token, java.util.Map<String, Object> metrics) {
    Node n = nodes.findByNodeToken(token)
        .orElseThrow(() -> ApiException.unauthorized("bad node token"));
    n.setLastSeenAt(java.time.Instant.now());
    if (n.getStatus() != NodeStatus.ONLINE) n.setStatus(NodeStatus.ONLINE);
    if (metrics != null && !metrics.isEmpty()) n.setCapacityJson(metrics);
    return nodes.save(n);
  }

  /**
   * detail.md Milestone 1.2: seed an allowPorts-matching port range into ports.
   * Idempotent — existing (protocol, number) rows are skipped.
   */
  @Transactional
  public int seedPorts(String actor, UUID nodeId, String protocol, int start, int end) {
    Node node = nodes.findById(nodeId).orElseThrow(() -> ApiException.notFound("node"));
    if (start < 1 || end > 65535 || start > end) throw ApiException.badRequest("invalid port range");
    if (end - start > 50_000) throw ApiException.badRequest("range too large");

    var existing = new java.util.HashSet<Integer>();
    for (Port p : ports.findByNodeIdAndStatus(nodeId, PortStatus.AVAILABLE)) existing.add(p.getPortNumber());
    // also skip ports in any other state via candidate check below is unnecessary for seeding

    List<Port> batch = new ArrayList<>();
    for (int pn = start; pn <= end; pn++) {
      if (existing.contains(pn)) continue;
      if (ports.findByNodeIdAndProtocolAndPortNumberAndStatusIn(nodeId, protocol, pn,
              List.of(PortStatus.RESERVED, PortStatus.ALLOCATED, PortStatus.ACTIVE)).isPresent()) continue;
      Port p = new Port();
      p.setNodeId(nodeId);
      p.setProtocol(protocol);
      p.setPortNumber(pn);
      p.setStatus(PortStatus.AVAILABLE);
      batch.add(p);
    }
    ports.saveAll(batch);
    audit.log(actor, "ADMIN", "node.seed_ports", "node", nodeId.toString(), "SUCCESS",
        java.util.Map.of("protocol", protocol, "start", start, "end", end, "created", batch.size()));
    return batch.size();
  }
}
