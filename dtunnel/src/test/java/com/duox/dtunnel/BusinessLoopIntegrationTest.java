package com.duox.dtunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * detail.md Milestone 1 verification: the one business loop end-to-end over
 * HTTP — register → login → node + port pool → resource request → approve →
 * agent register → tunnel create → agent config poll → heartbeat → ACTIVE.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BusinessLoopIntegrationTest {

  @Container
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

  @Container
  static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.context.ApplicationContext context;

  static String userCookie;
  static String adminCookie;
  static String nodeId;
  static String allocationId;
  static String agentId;
  static String agentToken;
  static String tunnelId;

  private String doPost(String cookie, String url, Object body) throws Exception {
    MvcResult res = mvc.perform(post(url)
            .cookie(cookie == null ? new jakarta.servlet.http.Cookie("x", "x") : sessionCookie(cookie))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body == null ? "{}" : json.writeValueAsString(body)))
        .andReturn();
    if (res.getResponse().getStatus() >= 400) {
      throw new AssertionError(url + " -> " + res.getResponse().getStatus() + " " + res.getResponse().getContentAsString());
    }
    return res.getResponse().getContentAsString();
  }

  private String doGet(String cookie, String url) throws Exception {
    MvcResult res = mvc.perform(get(url).cookie(sessionCookie(cookie))).andReturn();
    if (res.getResponse().getStatus() >= 400) {
      throw new AssertionError(url + " -> " + res.getResponse().getStatus() + " " + res.getResponse().getContentAsString());
    }
    return res.getResponse().getContentAsString();
  }

  private jakarta.servlet.http.Cookie sessionCookie(String value) {
    return new jakarta.servlet.http.Cookie("SESSION", value);
  }

  private String extractSession(MvcResult res) {
    var cookie = res.getResponse().getCookie("SESSION");
    return cookie == null ? null : cookie.getValue();
  }

  @Test @Order(1)
  void registerAndLogin() throws Exception {
    MvcResult reg = mvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", "user@example.com", "password", "password123"))))
        .andExpect(status().isOk())
        .andReturn();
    userCookie = extractSession(reg);
    assert userCookie != null : "no SESSION cookie after register";

    MvcResult login = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", "admin@duox.local", "password", "admin-change-me"))))
        .andExpect(status().isOk())
        .andReturn();
    adminCookie = extractSession(login);
    assert adminCookie != null : "no SESSION cookie for bootstrap superadmin";
  }

  @Test @Order(2)
  void superadminRegistersNodeAndSeedsPorts() throws Exception {
    String nodeJson = doPost(adminCookie, "/api/v1/nodes",
        Map.of("code", "VN-01", "region", "vietnam", "publicAddress", "127.0.0.1"));
    nodeId = json.readTree(nodeJson).get("id").asText();

    String seed = doPost(adminCookie, "/api/v1/nodes/" + nodeId + "/ports/seed",
        Map.of("protocol", "TCP", "start", 20000, "end", 20010));
    assert json.readTree(seed).get("created").asInt() == 11;
  }

  @Test @Order(3)
  void userSubmitsRequestAndAdminApproves() throws Exception {
    String reqJson = doPost(userCookie, "/api/v1/resource-requests",
        Map.of("nodeId", nodeId, "protocol", "TCP", "preferredPort", 20005, "durationDays", 30, "purpose", "minecraft"));
    String requestId = json.readTree(reqJson).get("id").asText();
    assert "PENDING".equals(json.readTree(reqJson).get("status").asText());

    String approval = doPost(adminCookie, "/api/v1/resource-requests/" + requestId + "/approve", null);
    JsonNode a = json.readTree(approval);
    assert "ALLOCATED".equals(a.get("status").asText());
    allocationId = a.get("allocationId").asText();
  }

  @Test @Order(4)
  void agentRegistersAndGetsApproved() throws Exception {
    String reg = doPost(null, "/agent/v1/register",
        Map.of("email", "user@example.com", "password", "password123",
               "publicKey", "dGVzdC1kZXZpY2Uta2V5LTE=", "platform", "linux", "agentVersion", "0.1.0"));
    JsonNode r = json.readTree(reg);
    agentId = r.get("agentId").asText();
    agentToken = r.get("token").asText();
    assert "PENDING".equals(r.get("status").asText());

    doPost(adminCookie, "/api/v1/agents/" + agentId + "/approve", null);
  }

  @Test @Order(5)
  void userCreatesTunnel() throws Exception {
    String t = doPost(userCookie, "/api/v1/tunnels",
        Map.of("allocationId", allocationId, "agentId", agentId, "name", "mc",
               "targetHost", "127.0.0.1", "targetPort", 25565));
    JsonNode tj = json.readTree(t);
    tunnelId = tj.get("id").asText();
    assert "CONFIGURED".equals(tj.get("status").asText());
  }

  @Test @Order(6)
  void agentPollsConfigAndSeesTunnel() throws Exception {
    MvcResult res = mvc.perform(get("/agent/v1/config")
            .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode cfg = json.readTree(res.getResponse().getContentAsString());
    assert cfg.get("version").asInt() >= 1 : "expected config version >= 1";
    JsonNode proxies = cfg.get("payload").get("proxies");
    assert proxies.size() == 1 : "expected exactly one desired proxy";
    JsonNode p = proxies.get(0);
    assert p.get("remotePort").asInt() == 20005 : "preferred port should win the allocation";
    assert p.get("localPort").asInt() == 25565;
    assert "tcp".equals(p.get("type").asText());
  }

  @Test @Order(7)
  void heartbeatMarksTunnelActive() throws Exception {
    MvcResult res = mvc.perform(post("/agent/v1/heartbeat")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "appliedVersion", 1,
                "agentVersion", "0.1.0",
                "tunnels", List.of(Map.of("tunnelId", tunnelId, "status", "RUNNING"))))))
        .andExpect(status().isOk())
        .andReturn();
    json.readTree(res.getResponse().getContentAsString());

    String tunnels = doGet(userCookie, "/api/v1/tunnels");
    JsonNode arr = json.readTree(tunnels);
    boolean active = false;
    for (JsonNode t : arr) {
      if (t.get("id").asText().equals(tunnelId) && "ACTIVE".equals(t.get("status").asText())) active = true;
    }
    assert active : "tunnel should be ACTIVE after heartbeat reports RUNNING";
  }

  @Test @Order(8)
  void frpPluginAllowsAuthorizedNewProxy() throws Exception {
    // Login op with agent identity in the frpc user field
    String userField = agentId + "." + agentToken;
    MvcResult login = mvc.perform(post("/agent/v1/frp-plugin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "version", "0.63.0", "op", "Login",
                "content", Map.of("user", userField, "remoteAddr", "1.2.3.4:5678")))))
        .andExpect(status().isOk())
        .andReturn();
    assert !json.readTree(login.getResponse().getContentAsString()).get("reject").asBoolean();

    // NewProxy for the allocated port is allowed.
    // Real frp protocol: NewProxy wraps identity in a UserInfo object
    // (pkg/plugin/server/types.go), unlike Login's flat string.
    Map<String, Object> userInfo = Map.of("user", userField, "metas", Map.of(), "run_id", "test-run");
    MvcResult np = mvc.perform(post("/agent/v1/frp-plugin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "version", "0.63.0", "op", "NewProxy",
                "content", Map.of(
                    "user", userInfo,
                    "proxy_name", userField + ".tunnel-" + tunnelId,
                    "proxy_type", "tcp",
                    "remote_port", 20005)))))
        .andExpect(status().isOk())
        .andReturn();
    assert !json.readTree(np.getResponse().getContentAsString()).get("reject").asBoolean()
        : "authorized NewProxy must be allowed";

    // NewProxy for a port that was never allocated is denied
    MvcResult bad = mvc.perform(post("/agent/v1/frp-plugin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "version", "0.63.0", "op", "NewProxy",
                "content", Map.of(
                    "user", userInfo,
                    "proxy_name", userField + ".tunnel-" + tunnelId,
                    "proxy_type", "tcp",
                    "remote_port", 20099)))))
        .andExpect(status().isOk())
        .andReturn();
    assert json.readTree(bad.getResponse().getContentAsString()).get("reject").asBoolean()
        : "mismatched remote_port must be denied";
  }

  @Test @Order(9)
  void auditTrailCaptured() throws Exception {
    String audits = doGet(adminCookie, "/api/v1/audits");
    JsonNode arr = json.readTree(audits);
    assert arr.size() > 0 : "audit trail must not be empty";
  }

  /** detail.md Milestone 3.1: one agent carries multiple tunnels at once. */
  @Test @Order(10)
  void multipleTunnelsPerAgent() throws Exception {
    // second request → second allocation on a different port
    String reqJson = doPost(userCookie, "/api/v1/resource-requests",
        Map.of("nodeId", nodeId, "protocol", "TCP", "preferredPort", 20006, "durationDays", 30, "purpose", "second"));
    String requestId = json.readTree(reqJson).get("id").asText();
    String approval = doPost(adminCookie, "/api/v1/resource-requests/" + requestId + "/approve", null);
    String alloc2 = json.readTree(approval).get("allocationId").asText();

    String t2 = doPost(userCookie, "/api/v1/tunnels",
        Map.of("allocationId", alloc2, "agentId", agentId, "name", "second",
               "targetHost", "127.0.0.1", "targetPort", 25566));
    String tunnel2Id = json.readTree(t2).get("id").asText();

    // agent's desired state now carries BOTH proxies
    MvcResult res = mvc.perform(get("/agent/v1/config")
            .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode proxies = json.readTree(res.getResponse().getContentAsString()).get("payload").get("proxies");
    assert proxies.size() == 2 : "agent should see two desired proxies, got " + proxies.size();

    java.util.Set<Integer> remotePorts = new java.util.HashSet<>();
    for (JsonNode p : proxies) remotePorts.add(p.get("remotePort").asInt());
    assert remotePorts.contains(20005) && remotePorts.contains(20006)
        : "both allocated ports must be present: " + remotePorts;

    // heartbeat marks the second tunnel ACTIVE too
    mvc.perform(post("/agent/v1/heartbeat")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "appliedVersion", 2,
                "tunnels", List.of(
                    Map.of("tunnelId", tunnelId, "status", "RUNNING"),
                    Map.of("tunnelId", tunnel2Id, "status", "RUNNING"))))))
        .andExpect(status().isOk());

    String tunnels = doGet(userCookie, "/api/v1/tunnels");
    int active = 0;
    for (JsonNode t : json.readTree(tunnels)) {
      if ("ACTIVE".equals(t.get("status").asText())) active++;
    }
    assert active == 2 : "both tunnels should be ACTIVE, got " + active;
  }

  /**
   * detail.md Milestone 3.3 + §1: usage is collected SERVER-SIDE from the
   * node's frps admin API (frpc v0.71 has no client traffic API). We stub
   * the frps endpoint, point the node at it, and run the collector.
   */
  @Test @Order(11)
  void usageMeteringFromFrps() throws Exception {
    // stub frps admin API serving per-proxy traffic counters
    com.sun.net.httpserver.HttpServer stub =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
    String body = json.writeValueAsString(Map.of("proxies", List.of(Map.of(
        "name", "someuser.tunnel-" + tunnelId,
        "type", "tcp",
        "todayTrafficIn", 12345,
        "todayTrafficOut", 67890,
        "status", "online"))));
    stub.createContext("/api/proxy/tcp", ex -> {
      byte[] b = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", "application/json");
      ex.sendResponseHeaders(200, b.length);
      ex.getResponseBody().write(b);
      ex.close();
    });
    stub.createContext("/api/proxy/udp", ex -> {
      byte[] b = "{\"proxies\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, b.length);
      ex.getResponseBody().write(b);
      ex.close();
    });
    stub.start();
    try {
      // point the node at the stub frps admin API
      String stubUrl = "http://127.0.0.1:" + stub.getAddress().getPort();
      var nodeRepo = context.getBean(com.duox.dtunnel.repo.NodeRepository.class);
      var node = nodeRepo.findById(java.util.UUID.fromString(nodeId)).orElseThrow();
      node.setFrpsAdminUrl(stubUrl);
      nodeRepo.save(node);

      // run the collector's lock-free body directly (the @SchedulerLock wrapper
      // would be skipped while the scheduled run holds the lock)
      context.getBean(com.duox.dtunnel.application.UsageCollectorJob.class).doCollect();

      String usage = doGet(userCookie, "/api/v1/tunnels/" + tunnelId + "/usage");
      JsonNode u = json.readTree(usage);
      assert u.get("bytesIn").asLong() >= 12345 : "bytesIn should be metered from frps";
      assert u.get("bytesOut").asLong() >= 67890 : "bytesOut should be metered from frps";
    } finally {
      stub.stop(0);
    }
  }

  @Test @Order(12)
  void revocationPropagatesToAgentAndFrpPlugin() throws Exception {
    // SUPERADMIN revokes the agent (terminal by design — device must re-register)
    MvcResult rev = mvc.perform(post("/api/v1/agents/" + agentId + "/revoke")
            .cookie(sessionCookie(adminCookie)))
        .andExpect(status().isOk())
        .andReturn();
    assert "REVOKED".equals(json.readTree(rev.getResponse().getContentAsString()).get("status").asText());

    // 1) Bearer token is rejected at the filter on the very next request (§4, §15)
    mvc.perform(get("/agent/v1/config")
            .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isUnauthorized());

    // 2) Heartbeat is rejected too
    mvc.perform(post("/agent/v1/heartbeat")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "appliedVersion", 1, "agentVersion", "0.1.0", "tunnels", List.of()))))
        .andExpect(status().isUnauthorized());

    // 3) FRP plugin denies Login for the revoked identity
    String userField = agentId + "." + agentToken;
    MvcResult login = mvc.perform(post("/agent/v1/frp-plugin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "version", "0.63.0", "op", "Login",
                "content", Map.of("user", userField, "remoteAddr", "1.2.3.4:5678")))))
        .andExpect(status().isOk())
        .andReturn();
    assert json.readTree(login.getResponse().getContentAsString()).get("reject").asBoolean()
        : "revoked agent Login must be denied";

    // 4) FRP plugin denies NewProxy for the revoked identity
    Map<String, Object> userInfo = Map.of("user", userField, "metas", Map.of(), "run_id", "rev-run");
    MvcResult np = mvc.perform(post("/agent/v1/frp-plugin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "version", "0.63.0", "op", "NewProxy",
                "content", Map.of(
                    "user", userInfo,
                    "proxy_name", userField + ".tunnel-" + tunnelId,
                    "proxy_type", "tcp",
                    "remote_port", 20005)))))
        .andExpect(status().isOk())
        .andReturn();
    assert json.readTree(np.getResponse().getContentAsString()).get("reject").asBoolean()
        : "revoked agent NewProxy must be denied";
  }

  @Test @Order(13)
  void usageAggregatesIntoDailyRollup() throws Exception {
    // Order 11 recorded 12345/67890 into usage_records for tunnelId (today).
    context.getBean(com.duox.dtunnel.application.UsageAggregateJob.class).doAggregate();

    String history = doGet(userCookie, "/api/v1/tunnels/" + tunnelId + "/usage/history?days=7");
    JsonNode h = json.readTree(history);
    JsonNode days = h.get("days");
    assert days.isArray() && days.size() >= 1 : "expected at least one daily rollup row";
    JsonNode today = days.get(0);
    assert today.get("bytesIn").asLong() >= 12345 : "daily bytesIn should include recorded usage";
    assert today.get("bytesOut").asLong() >= 67890 : "daily bytesOut should include recorded usage";

    // idempotent: running again must not double-count
    context.getBean(com.duox.dtunnel.application.UsageAggregateJob.class).doAggregate();
    String again = doGet(userCookie, "/api/v1/tunnels/" + tunnelId + "/usage/history?days=7");
    JsonNode days2 = json.readTree(again).get("days");
    assert days2.get(0).get("bytesIn").asLong() == today.get("bytesIn").asLong()
        : "aggregate must be idempotent (upsert, not accumulate)";
  }
}

