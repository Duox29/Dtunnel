package com.duox.dtunnel.api;

import com.duox.dtunnel.api.CurrentUser;
import com.duox.dtunnel.domain.Port;
import com.duox.dtunnel.domain.PortAllocation;
import com.duox.dtunnel.domain.PortStatus;
import com.duox.dtunnel.domain.User;
import com.duox.dtunnel.repo.PortAllocationRepository;
import com.duox.dtunnel.repo.PortRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** detail.md §8: GET /api/v1/ports — scoped to caller unless SUPERADMIN. */
@RestController
@RequestMapping("/api/v1/ports")
public class PortController {

  private final PortRepository ports;
  private final PortAllocationRepository allocations;
  private final CurrentUser currentUser;

  public PortController(PortRepository ports, PortAllocationRepository allocations, CurrentUser currentUser) {
    this.ports = ports;
    this.allocations = allocations;
    this.currentUser = currentUser;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    User u = currentUser.require();
    List<Port> result = currentUser.isSuperadmin()
        ? ports.findAll()
        : ports.findByOwnerUserIdAndStatusIn(u.getId(),
            List.of(PortStatus.RESERVED, PortStatus.ALLOCATED, PortStatus.ACTIVE, PortStatus.EXPIRED_PENDING_RELEASE));
    return result.stream().map(p -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", p.getId().toString());
      m.put("nodeId", p.getNodeId().toString());
      m.put("protocol", p.getProtocol());
      m.put("portNumber", p.getPortNumber());
      m.put("status", p.getStatus().name());
      allocations.findFirstByPortIdOrderByAllocatedAtDesc(p.getId()).ifPresent(a -> {
        m.put("allocationId", a.getId().toString());
        m.put("expiresAt", a.getExpiresAt().toString());
      });
      return m;
    }).toList();
  }
}
