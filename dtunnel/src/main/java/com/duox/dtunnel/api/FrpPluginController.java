package com.duox.dtunnel.api;

import com.duox.dtunnel.application.AuditService;
import com.duox.dtunnel.application.DesiredStateService;
import com.duox.dtunnel.domain.*;
import com.duox.dtunnel.repo.*;
import com.duox.dtunnel.security.AgentTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * detail.md §9: FRP HTTP server-plugin endpoint. frps calls back on
 * Login/NewProxy/Ping/CloseProxy; the control plane allows or denies each
 * attempt in real time. Identity travels in frpc's "user" field as
 * "<agentId>.<deviceToken>" — the plugin re-validates ownership and
 * allocation status on EVERY op (§15), so a revoked allocation fails the
 * next NewProxy even mid-session.
 */
@RestController
@RequestMapping("/agent/v1/frp-plugin")
public class FrpPluginController {

  private static final Logger log = LoggerFactory.getLogger(FrpPluginController.class);

  private final AgentRepository agents;
  private final AgentTokenService tokens;
  private final TunnelRepository tunnels;
  private final PortAllocationRepository allocations;
  private final PortRepository ports;
  private final AuditService audit;
  private final DesiredStateService desiredState;
  private final String pluginToken;

  public FrpPluginController(AgentRepository agents, AgentTokenService tokens, TunnelRepository tunnels,
                             PortAllocationRepository allocations, PortRepository ports, AuditService audit,
                             DesiredStateService desiredState,
                             @Value("${dtunnel.frp.plugin-token:}") String pluginToken) {
    this.agents = agents;
    this.tokens = tokens;
    this.tunnels = tunnels;
    this.allocations = allocations;
    this.ports = ports;
    this.audit = audit;
    this.desiredState = desiredState;
    this.pluginToken = pluginToken;
  }

  record PluginRequest(String version, String op, Map<String, Object> content) {}

  private Map<String, Object> allow() {
    return Map.of("reject", false, "unchange", true);
  }

  private Map<String, Object> deny(String reason) {
    log.info("frp-plugin deny: {}", reason);
    // frp's Response struct reads "reject_reason" (pkg/plugin/server/types.go)
    return Map.of("reject", true, "reject_reason", reason, "unchange", true);
  }

  @PostMapping
  @Transactional
  public Map<String, Object> handle(@RequestBody PluginRequest req,
                                    @org.springframework.web.bind.annotation.RequestHeader(value = "X-Frp-Plugin-Token", required = false) String headerToken,
                                    @org.springframework.web.bind.annotation.RequestParam(value = "token", required = false) String queryToken) {
    // frps server plugins cannot send custom headers (verified against frp
    // 0.63), so the shared secret may also travel as a query parameter.
    String presented = headerToken != null && !headerToken.isBlank() ? headerToken : queryToken;
    if (pluginToken != null && !pluginToken.isBlank() && !pluginToken.equals(presented)) {
      return deny("bad plugin token");
    }
    if (req == null || req.op() == null || req.content() == null) return deny("malformed request");
    String op = req.op();
    Map<String, Object> c = req.content();
    String userField = extractUserField(op, c);

    AgentIdentity ident = resolveIdentity(userField);
    if (ident == null) return deny("unresolvable agent identity");

    switch (op) {
      case "Login" -> {
        if (ident.agent.getStatus() == AgentStatus.REVOKED) return deny("agent revoked");
        ident.agent.setLastSeenAt(Instant.now());
        agents.save(ident.agent);
        audit.log(ident.agent.getId().toString(), "AGENT", "frp.login", "agent",
            ident.agent.getId().toString(), "SUCCESS", meta("remote", str(c.get("remote_addr"))));
        return allow();
      }
      case "NewProxy" -> {
        if (ident.agent.getStatus() == AgentStatus.REVOKED) return deny("agent revoked");
        String proxyName = str(c.get("proxy_name"));
        // frps prefixes proxy names with "<user>." when the client sets user
        String bare = proxyName != null && userField != null && proxyName.startsWith(userField + ".")
            ? proxyName.substring(userField.length() + 1) : proxyName;
        UUID tunnelId = parseTunnelId(bare);
        if (tunnelId == null) return deny("proxy name is not a platform tunnel id");

        Tunnel t = tunnels.findById(tunnelId).orElse(null);
        if (t == null || !t.getAgentId().equals(ident.agent.getId())) return deny("unknown tunnel or wrong agent");

        PortAllocation alloc = allocations.findById(t.getPortAllocationId()).orElse(null);
        if (alloc == null) return deny("no allocation");
        if (alloc.getExpiresAt().isBefore(Instant.now())) return deny("allocation expired");

        Port port = ports.findById(alloc.getPortId()).orElse(null);
        if (port == null) return deny("no port");

        Object remotePort = c.get("remote_port");
        if (remotePort instanceof Number n && n.intValue() != port.getPortNumber()) {
          return deny("remote_port does not match allocation");
        }
        String proxyType = str(c.get("proxy_type"));
        if (proxyType != null && !proxyType.equalsIgnoreCase(port.getProtocol())) {
          return deny("proxy type does not match allocation");
        }

        if (t.getStatus() == TunnelStatus.CONFIGURED || t.getStatus() == TunnelStatus.STARTING
            || t.getStatus() == TunnelStatus.STOPPED || t.getStatus() == TunnelStatus.ERROR) {
          t.setStatus(TunnelStatus.STARTING);
          tunnels.save(t);
        }
        if (port.getStatus() == PortStatus.ALLOCATED) {
          port.setStatus(PortStatus.ACTIVE);
          ports.save(port);
        }
        audit.log(ident.agent.getId().toString(), "AGENT", "frp.new_proxy", "tunnel",
            tunnelId.toString(), "SUCCESS", Map.of("remotePort", port.getPortNumber()));
        return allow();
      }
      case "Ping" -> {
        if (ident.agent.getStatus() == AgentStatus.REVOKED) return deny("agent revoked");
        ident.agent.setLastSeenAt(Instant.now());
        agents.save(ident.agent);
        return allow();
      }
      case "CloseProxy" -> {
        String proxyName = str(c.get("proxy_name"));
        String bare = proxyName != null && userField != null && proxyName.startsWith(userField + ".")
            ? proxyName.substring(userField.length() + 1) : proxyName;
        UUID tunnelId = parseTunnelId(bare);
        if (tunnelId != null) {
          Tunnel t = tunnels.findById(tunnelId).orElse(null);
          if (t != null && t.getAgentId().equals(ident.agent.getId())
              && (t.getStatus() == TunnelStatus.ACTIVE || t.getStatus() == TunnelStatus.STARTING)) {
            t.setStatus(TunnelStatus.ERROR); // unexpected close; reconciler may restart (§11)
            tunnels.save(t);
            // ERROR leaves the desired set → publish so the stored version bumps
            // and the agent converges; the reconciler re-adds it on recovery.
            desiredState.publish(t.getAgentId());
            audit.log(ident.agent.getId().toString(), "AGENT", "frp.close_proxy", "tunnel",
                tunnelId.toString(), "SUCCESS", null);
          }
        }
        return allow();
      }
      default -> {
        return allow(); // ops we don't police (NewWorkConn, NewUserConn...) pass through
      }
    }
  }

  /**
   * frp plugin protocol (pkg/plugin/server/types.go): Login carries the user
   * as a flat string (msg.Login); NewProxy/Ping/CloseProxy wrap it in a
   * UserInfo object {"user": ..., "metas": ..., "run_id": ...}.
   */
  private String extractUserField(String op, Map<String, Object> c) {
    Object u = c.get("user");
    if (u instanceof Map<?, ?> m) return str(m.get("user"));
    return str(u);
  }

  private record AgentIdentity(Agent agent) {}

  /** user field format: "<agentId>.<deviceToken>" */
  private AgentIdentity resolveIdentity(String userField) {
    if (userField == null) return null;
    int dot = userField.indexOf('.');
    if (dot <= 0) return null;
    UUID agentId;
    try { agentId = UUID.fromString(userField.substring(0, dot)); } catch (RuntimeException e) { return null; }
    String token = userField.substring(dot + 1);
    UUID resolved = tokens.resolve(token);
    if (resolved == null || !resolved.equals(agentId)) return null;
    return agents.findById(agentId).map(AgentIdentity::new).orElse(null);
  }

  private UUID parseTunnelId(String proxyName) {
    if (proxyName == null || !proxyName.startsWith("tunnel-")) return null;
    try { return UUID.fromString(proxyName.substring("tunnel-".length())); } catch (RuntimeException e) { return null; }
  }

  private String str(Object o) { return o == null ? null : String.valueOf(o); }

  /** Map.of rejects nulls; audit metadata is optional by nature. */
  private Map<String, Object> meta(String k, Object v) {
    if (v == null) return Map.of();
    return Map.of(k, v);
  }
}
