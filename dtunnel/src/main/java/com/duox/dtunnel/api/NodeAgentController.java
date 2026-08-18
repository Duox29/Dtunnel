package com.duox.dtunnel.api;

import com.duox.dtunnel.application.NodeService;
import com.duox.dtunnel.domain.Node;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * detail.md §1/§3.4: Gateway Node Agent channel. The node agent (duox-node-agent)
 * reports health/capacity on a heartbeat, authenticated by the per-node shared
 * secret issued at registration. Distinct from the user-agent channel
 * (/agent/v1) because the two have different privilege/deployment models (§16).
 */
@RestController
@RequestMapping("/node/v1")
public class NodeAgentController {

  private final NodeService nodeService;

  public NodeAgentController(NodeService nodeService) {
    this.nodeService = nodeService;
  }

  public record HeartbeatRequest(Map<String, Object> metrics) {}

  @PostMapping("/heartbeat")
  public Map<String, Object> heartbeat(@RequestHeader("Authorization") String auth,
                                       @RequestBody(required = false) HeartbeatRequest body) {
    String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
    if (token == null || token.isBlank()) throw com.duox.dtunnel.application.ApiException.unauthorized("missing node token");
    Node n = nodeService.heartbeat(token, body == null ? null : body.metrics());
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("nodeId", n.getId().toString());
    m.put("code", n.getCode());
    m.put("status", n.getStatus().name());
    return m;
  }
}
