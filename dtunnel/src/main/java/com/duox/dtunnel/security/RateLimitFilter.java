package com.duox.dtunnel.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.RecoveryStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * detail.md §15 + Milestone 4.3: distributed rate limiting (Bucket4j + Redis,
 * shared across control-plane instances). Limits apply per client IP for the
 * unauthenticated endpoints and per principal for the authenticated ones:
 *
 *   POST /api/v1/auth/register, /api/v1/auth/login  -> 10/min per IP
 *   POST /agent/v1/register                          -> 5/min per IP
 *   POST /api/v1/resource-requests                   -> 30/hour per user
 *   POST /api/v1/nodes/{id}/ping                     -> 60/min per user
 *
 * Rejected requests get 429 + Retry-After. Bucket state lives in Redis so
 * every instance enforces the same budget.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  private final boolean enabled;
  private final LettuceBasedProxyManager<byte[]> proxyManager;
  private final RedisClient redisClient;
  private final StatefulRedisConnection<byte[], byte[]> connection;

  public RateLimitFilter(@Value("${dtunnel.ratelimit.enabled:true}") boolean enabled,
                         @Value("${spring.data.redis.host:localhost}") String host,
                         @Value("${spring.data.redis.port:6379}") int port,
                         @Value("${spring.data.redis.password:}") String password) {
    this.enabled = enabled;
    RedisURI uri = RedisURI.create(host, port);
    // Lettuce 7 (Boot 4): credentials go through a provider, not setPassword.
    if (password != null && !password.isBlank()) {
      uri.setCredentialsProvider(
          RedisCredentialsProvider.from(() -> RedisCredentials.just(null, password)));
    }
    this.redisClient = RedisClient.create(uri);
    this.connection = redisClient.connect(new ByteArrayCodec());
    this.proxyManager = LettuceBasedProxyManager.builderFor(connection).build();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (!enabled || !"POST".equalsIgnoreCase(req.getMethod())) {
      chain.doFilter(req, res);
      return;
    }
    String path = req.getRequestURI();

    Limit limit = limitFor(path);
    if (limit == null) {
      chain.doFilter(req, res);
      return;
    }
    String identity = limit.perUser && req.getUserPrincipal() != null
        ? "u:" + req.getUserPrincipal().getName()
        : "ip:" + clientIp(req);
    byte[] key = ("dtunnel:rl:" + limit.group + ":" + identity).getBytes(StandardCharsets.UTF_8);

    BucketConfiguration config = BucketConfiguration.builder()
        .addLimit(Bandwidth.builder()
            .capacity(limit.capacity)
            .refillGreedy(limit.capacity, limit.window)
            .build())
        .build();
    BucketProxy bucket = proxyManager.builder()
        .withRecoveryStrategy(RecoveryStrategy.RECONSTRUCT)
        .build(key, config);

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      chain.doFilter(req, res);
      return;
    }
    long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
    log.info("rate limit hit: {} {} ({}), retry in {}s", req.getMethod(), path, identity, retryAfterSeconds);
    res.setStatus(429);
    res.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    res.setContentType("application/json");
    res.getWriter().write("{\"error\":\"rate limit exceeded\"}");
  }

  private record Limit(String group, long capacity, Duration window, boolean perUser) {}

  private Limit limitFor(String path) {
    if (path.equals("/api/v1/auth/register") || path.equals("/api/v1/auth/login")) {
      return new Limit("auth", 10, Duration.ofMinutes(1), false);
    }
    if (path.equals("/agent/v1/register")) {
      return new Limit("agent-register", 5, Duration.ofMinutes(1), false);
    }
    if (path.equals("/api/v1/resource-requests")) {
      return new Limit("resource-requests", 30, Duration.ofHours(1), true);
    }
    if (path.matches("/api/v1/nodes/[^/]+/ping")) {
      return new Limit("node-ping", 60, Duration.ofMinutes(1), true);
    }
    return null;
  }

  private String clientIp(HttpServletRequest req) {
    String fwd = req.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    return req.getRemoteAddr();
  }

  @Override
  public void destroy() {
    try { connection.close(); } catch (RuntimeException ignored) { }
    try { redisClient.shutdown(); } catch (RuntimeException ignored) { }
  }
}
