package com.duox.dtunnel.config;

import com.duox.dtunnel.domain.Role;
import com.duox.dtunnel.domain.User;
import com.duox.dtunnel.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Seeds the initial SUPERADMIN so the approval loop is usable from first boot. */
@Component
public class BootstrapData implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapData.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final String email;
  private final String password;

  public BootstrapData(UserRepository users, PasswordEncoder encoder,
                       @Value("${dtunnel.bootstrap.superadmin.email}") String email,
                       @Value("${dtunnel.bootstrap.superadmin.password}") String password) {
    this.users = users;
    this.encoder = encoder;
    this.email = email;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (users.findByEmailIgnoreCase(email).isEmpty()) {
      User admin = new User();
      admin.setEmail(email);
      admin.setPasswordHash(encoder.encode(password));
      admin.setRole(Role.SUPERADMIN);
      admin.setEmailVerifiedAt(Instant.now());
      users.save(admin);
      log.warn("Seeded bootstrap SUPERADMIN {} — change this password immediately", email);
    }
  }
}
