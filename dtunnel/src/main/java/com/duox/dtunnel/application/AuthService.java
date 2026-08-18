package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.Role;
import com.duox.dtunnel.domain.User;
import com.duox.dtunnel.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AuditService audit;

  public AuthService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
    this.users = users;
    this.encoder = encoder;
    this.audit = audit;
  }

  @Transactional
  public User register(String email, String password) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmailIgnoreCase(normalized)) {
      throw ApiException.conflict("email already registered");
    }
    User u = new User();
    u.setEmail(normalized);
    u.setPasswordHash(encoder.encode(password));
    u.setRole(Role.USER);
    u.setEmailVerifiedAt(Instant.now()); // MVP: no email loop yet
    users.save(u);
    audit.log(normalized, "USER", "auth.register", "user", u.getId().toString(), "SUCCESS", null);
    return u;
  }
}
