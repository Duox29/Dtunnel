package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.*;
import com.duox.dtunnel.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Builds the agent's desired-state payload and bumps configuration_versions.
 * detail.md Milestone 1.5: tunnel creation writes a new desired-state version;
 * the agent polls /agent/v1/config and reconciles.
 */
@Service
public class DesiredStateService {

  private final TunnelRepository tunnels;
  private final PortAllocationRepository allocations;
  private final PortRepository ports;
  private final NodeRepository nodes;
  private final ConfigurationVersionRepository versions;

  public DesiredStateService(TunnelRepository tunnels, PortAllocationRepository allocations,
                             PortRepository ports, NodeRepository nodes,
                             ConfigurationVersionRepository versions) {
    this.tunnels = tunnels;
    this.allocations = allocations;
    this.ports = ports;
    this.nodes = nodes;
    this.versions = versions;
  }

  /** Tunnels that should be running on the agent right now. */
  private List<Tunnel> desiredTunnels(UUID agentId) {
    return tunnels.findByAgentId(agentId).stream()
        .filter(t -> EnumSet.of(TunnelStatus.CONFIGURED, TunnelStatus.STARTING, TunnelStatus.ACTIVE)
            .contains(t.getStatus()))
        .toList();
  }

  public int currentVersion(UUID agentId) {
    return versions.latestVersion(agentId);
  }

  /**
   * detail.md §11: configuration_versions is the single source of truth for
   * what the agent should run. /agent/v1/config returns this STORED payload so
   * the version number always matches what the agent applies — a live rebuild
   * here can diverge from the stored version and strand the agent.
   */
  public Map<String, Object> storedConfig(UUID agentId) {
    return versions.findTopByAgentIdOrderByVersionDesc(agentId)
        .map(cv -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("version", cv.getVersion());
          m.put("payload", cv.getPayload());
          return m;
        })
        .orElseGet(() -> Map.of(
            "version", 0,
            "payload", Map.of("agentId", agentId.toString(), "proxies", List.of())));
  }

  public Map<String, Object> buildPayload(UUID agentId) {
    List<Map<String, Object>> proxies = new ArrayList<>();
    for (Tunnel t : desiredTunnels(agentId)) {
      PortAllocation alloc = allocations.findById(t.getPortAllocationId()).orElse(null);
      if (alloc == null) continue;
      Port port = ports.findById(alloc.getPortId()).orElse(null);
      if (port == null) continue;
      Node node = nodes.findById(port.getNodeId()).orElse(null);
      if (node == null) continue;

      Map<String, Object> proxy = new LinkedHashMap<>();
      proxy.put("tunnelId", t.getId().toString());
      proxy.put("name", "tunnel-" + t.getId());
      proxy.put("type", port.getProtocol().toLowerCase()); // tcp | udp
      proxy.put("serverAddr", node.getPublicAddress());
      proxy.put("serverPort", 7000); // frps bindPort, detail.md §9
      proxy.put("remotePort", port.getPortNumber());
      proxy.put("localHost", t.getTargetHost());
      proxy.put("localPort", t.getTargetPort());
      if (t.getBandwidthLimitMbps() != null) proxy.put("bandwidthLimitMbps", t.getBandwidthLimitMbps());
      proxies.add(proxy);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("agentId", agentId.toString());
    payload.put("proxies", proxies);
    return payload;
  }

  /** Writes a new configuration version if the payload changed. Returns the version number. */
  @Transactional
  public int publish(UUID agentId) {
    Map<String, Object> payload = buildPayload(agentId);
    int latest = versions.latestVersion(agentId);
    Optional<ConfigurationVersion> last = versions.findTopByAgentIdOrderByVersionDesc(agentId);
    if (last.isPresent() && last.get().getPayload().equals(payload)) {
      return latest;
    }
    ConfigurationVersion cv = new ConfigurationVersion();
    cv.setAgentId(agentId);
    cv.setVersion(latest + 1);
    cv.setPayload(payload);
    versions.save(cv);
    return latest + 1;
  }
}
