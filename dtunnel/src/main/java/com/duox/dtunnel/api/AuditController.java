package com.duox.dtunnel.api;

import com.duox.dtunnel.domain.Audit;
import com.duox.dtunnel.repo.AuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** detail.md §15: SUPERADMIN audit trail access. */
@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

  private final AuditRepository audits;
  private final CurrentUser currentUser;

  public AuditController(AuditRepository audits, CurrentUser currentUser) {
    this.audits = audits;
    this.currentUser = currentUser;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
    currentUser.requireSuperadmin();
    return audits.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 200)))
        .map(AuditController::json).getContent();
  }

  static Map<String, Object> json(Audit a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId());
    m.put("actor", a.getActor());
    m.put("actorType", a.getActorType());
    m.put("action", a.getAction());
    m.put("resourceType", a.getResourceType());
    m.put("resourceId", a.getResourceId());
    m.put("result", a.getResult());
    m.put("sourceIp", a.getSourceIp());
    m.put("metadata", a.getMetadata());
    m.put("createdAt", a.getCreatedAt().toString());
    return m;
  }
}
