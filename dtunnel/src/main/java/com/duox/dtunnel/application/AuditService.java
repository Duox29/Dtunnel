package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.Audit;
import com.duox.dtunnel.repo.AuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/** detail.md §15: audit every mutating action, including source IP. */
@Service
public class AuditService {

  private final AuditRepository audits;

  public AuditService(AuditRepository audits) {
    this.audits = audits;
  }

  public void log(String actor, String actorType, String action,
                  String resourceType, String resourceId, String result, Map<String, Object> metadata) {
    Audit a = new Audit();
    a.setActor(actor);
    a.setActorType(actorType);
    a.setAction(action);
    a.setResourceType(resourceType);
    a.setResourceId(resourceId);
    a.setResult(result);
    a.setSourceIp(currentIp());
    a.setMetadata(metadata);
    audits.save(a);
  }

  private String currentIp() {
    try {
      ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs == null) return null;
      HttpServletRequest req = attrs.getRequest();
      String fwd = req.getHeader("X-Forwarded-For");
      return fwd != null ? fwd.split(",")[0].trim() : req.getRemoteAddr();
    } catch (RuntimeException e) {
      return null;
    }
  }
}
