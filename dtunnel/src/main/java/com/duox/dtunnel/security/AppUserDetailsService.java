package com.duox.dtunnel.security;

import com.duox.dtunnel.repo.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  public AppUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    com.duox.dtunnel.domain.User u = users.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new UsernameNotFoundException("unknown user: " + email));
    return new User(u.getEmail(), u.getPasswordHash() != null ? u.getPasswordHash() : "{noop}__disabled__",
        List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
  }
}
