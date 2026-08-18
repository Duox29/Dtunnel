package com.duox.dtunnel.api;

import com.duox.dtunnel.application.ApiException;
import com.duox.dtunnel.application.AuthService;
import com.duox.dtunnel.domain.User;
import com.duox.dtunnel.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** detail.md §8: /api/v1/auth/* — session-cookie auth for the web UI. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  public record RegisterRequest(@Email @NotBlank String email,
                                @NotBlank @Size(min = 8, max = 128) String password) {}
  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

  private final AuthService authService;
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final CurrentUser currentUser;
  private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

  public AuthController(AuthService authService, UserRepository users,
                        PasswordEncoder encoder, CurrentUser currentUser) {
    this.authService = authService;
    this.users = users;
    this.encoder = encoder;
    this.currentUser = currentUser;
  }

  @PostMapping("/register")
  public Map<String, Object> register(@jakarta.validation.Valid @RequestBody RegisterRequest req,
                                      HttpServletRequest request, HttpServletResponse response) {
    User u = authService.register(req.email(), req.password());
    establishSession(u, request, response);
    return userJson(u);
  }

  @PostMapping("/login")
  public Map<String, Object> login(@jakarta.validation.Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
    User u = users.findByEmailIgnoreCase(req.email().trim())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    if (u.getPasswordHash() == null || !encoder.matches(req.password(), u.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials");
    }
    establishSession(u, request, response);
    return userJson(u);
  }

  @PostMapping("/logout")
  public Map<String, String> logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("status", "logged_out");
  }

  @GetMapping("/me")
  public Map<String, Object> me() {
    return userJson(currentUser.require());
  }

  /** Session-fixation defense: rotate the session, then persist the SecurityContext into it. */
  private void establishSession(User u, HttpServletRequest request, HttpServletResponse response) {
    HttpSession old = request.getSession(false);
    if (old != null) old.invalidate();
    request.getSession(true);

    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
    var token = new UsernamePasswordAuthenticationToken(u.getEmail(), null, authorities);
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(token);
    SecurityContextHolder.setContext(ctx);
    contextRepository.saveContext(ctx, request, response);
  }

  static Map<String, Object> userJson(User u) {
    return Map.of("id", u.getId().toString(), "email", u.getEmail(),
        "role", u.getRole().name(), "plan", u.getPlan());
  }
}
