package com.duox.dtunnel.security;

import com.duox.dtunnel.domain.Agent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * detail.md §6/§15: after keypair-based auth the agent gets a short-lived,
 * scoped session token — the long-term key itself is never sent on every request.
 * Backed by Redis so every control-plane instance shares the token store.
 */
@Service
public class AgentTokenService {

  private static final String KEY_PREFIX = "dtunnel:agent-token:";

  private final StringRedisTemplate redis;
  private final Duration ttl;
  private final SecureRandom random = new SecureRandom();

  public AgentTokenService(StringRedisTemplate redis,
                           @Value("${dtunnel.agent.token-ttl:PT24H}") Duration ttl) {
    this.redis = redis;
    this.ttl = ttl;
  }

  public String issue(Agent agent) {
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    redis.opsForValue().set(KEY_PREFIX + token, agent.getId().toString(), ttl);
    return token;
  }

  public UUID resolve(String token) {
    if (token == null || token.isBlank()) return null;
    String value = redis.opsForValue().get(KEY_PREFIX + token);
    return value == null ? null : UUID.fromString(value);
  }

  public void touch(String token) {
    redis.expire(KEY_PREFIX + token, ttl);
  }

  public void revoke(String token) {
    if (token != null) redis.delete(KEY_PREFIX + token);
  }

  public void revokeAllForAgent(UUID agentId) {
    // tokens are opaque and per-issue; revocation of an agent is enforced by
    // checking agents.status on every request (see AgentTokenFilter)
  }
}
