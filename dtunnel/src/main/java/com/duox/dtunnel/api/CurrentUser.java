package com.duox.dtunnel.api;

import com.duox.dtunnel.application.ApiException;
import com.duox.dtunnel.domain.Role;
import com.duox.dtunnel.domain.User;
import com.duox.dtunnel.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Resolves the session-authenticated web user for /api/v1 controllers. */
@Component
public class CurrentUser {

  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public User require() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
      throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "not authenticated");
    }
    return users.findByEmailIgnoreCase(auth.getName())
        .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "unknown user"));
  }

  public UUID id() { return require().getId(); }

  public boolean isSuperadmin() { return require().getRole() == Role.SUPERADMIN; }

  public User requireSuperadmin() {
    User u = require();
    if (u.getRole() != Role.SUPERADMIN) throw ApiException.forbidden("SUPERADMIN required");
    return u;
  }
}
