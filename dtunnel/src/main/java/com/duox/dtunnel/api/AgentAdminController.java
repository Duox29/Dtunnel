package com.duox.dtunnel.api;

import com.duox.dtunnel.application.AgentService;
import com.duox.dtunnel.domain.Agent;
import com.duox.dtunnel.domain.User;
import com.duox.dtunnel.repo.AgentRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** detail.md Milestone 1.4 + §15: SUPERADMIN approves/revokes registered devices. */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentAdminController {

  private final AgentRepository agents;
  private final AgentService agentService;
  private final CurrentUser currentUser;

  public AgentAdminController(AgentRepository agents, AgentService agentService, CurrentUser currentUser) {
    this.agents = agents;
    this.agentService = agentService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    User u = currentUser.require();
    List<Agent> result = currentUser.isSuperadmin() ? agents.findAll() : agents.findByUserId(u.getId());
    return result.stream().map(AgentAdminController::json).toList();
  }

  @PostMapping("/{id}/approve")
  public Map<String, Object> approve(@PathVariable UUID id) {
    User admin = currentUser.requireSuperadmin();
    return json(agentService.approve(admin.getId(), id));
  }

  @PostMapping("/{id}/revoke")
  public Map<String, Object> revoke(@PathVariable UUID id) {
    User admin = currentUser.requireSuperadmin();
    return json(agentService.revoke(admin.getId(), id));
  }

  static Map<String, Object> json(Agent a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId().toString());
    m.put("userId", a.getUserId().toString());
    m.put("publicKey", a.getPublicKey());
    m.put("platform", a.getPlatform());
    m.put("agentVersion", a.getAgentVersion());
    m.put("status", a.getStatus().name());
    m.put("lastSeenAt", a.getLastSeenAt() == null ? null : a.getLastSeenAt().toString());
    m.put("createdAt", a.getCreatedAt().toString());
    return m;
  }
}
