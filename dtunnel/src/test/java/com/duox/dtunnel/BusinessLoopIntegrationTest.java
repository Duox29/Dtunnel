package com.duox.dtunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

  static String userCookie;
  static String adminCookie;
  static String nodeId;
  static String allocationId;
  static String agentId;
  static String agentToken;
  static String tunnelId;

  private String post(String cookie, String url, Object body) throws Exception {
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

  private String get(String cookie, String url) throws Exception {
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
    String nodeJson = post(adminCookie, "/api/v1/nodes",
        Map.of("code", "VN-01", "region", "vietnam", "publicAddress", "127.0.0.1"));
    nodeId = json.readTree(nodeJson).get("id").asText();

    String seed = post(adminCookie, "/api/v1/nodes/" + nodeId + "/ports/seed",
        Map.of("protocol", "TCP", "start", 20000, "end", 20010));
    assert json.readTree(seed).get("created").asInt() == 11;
  }

  @Test @Order(3)
  void userSubmitsRequestAndAdminApproves() throws Exception {
    String reqJson = post(userCookie, "/api/v1/resource-requests",
        Map.of("nodeId", nodeId, "protocol", "TCP", "preferredPort", 20005, "durationDays", 30, "purpose", "minecraft"));
    String requestId = json.readTree(reqJson).get("id").asText();
    assert "PENDING".equals(json.readTree(reqJson).get("status").asText());

    String approval = post(adminCookie, "/api/v1/resource-requests/" + requestId + "/approve", null);
    JsonNode a = json.readTree(approval);
    assert "ALLOCATED".equals(a.get("status").asText());
    allocationId = a.get("allocationId").asText();
  }

  @Test @Order(4)
  void agentRegistersAndGetsApproved() throws Exception {
    String reg = post(null, "/agent/v1/register",
        Map.of("email", "user@example.com", "password", "password123",
               "publicKey", "dGVzdC1kZXZpY2Uta2V5LTE=", "platform", "linux", "agentVersion", "0.1.0"));
    JsonNode r = json.readTree(reg);
    agentId = r.get("agentId").asText();
    agentToken = r.get("token").asText();
    assert "PENDING".equals(r.get("status").asText());

    post(adminCookie, "/api/v1/agents/" + agentId + "/approve", null);
  }

  @Test @Order(5)
  void userCreatesTunnel() throws Exception {
    String t = post(userCookie, "/api/v1/tunnels",
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

    String tunnels = get(userCookie, "/api/v1/tunnels");
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

    // NewProxy for the allocated port is allowed
    MvcResult np = mvc.perform(post("/agent/v1/frp-plugin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "version", "0.63.0", "op", "NewProxy",
                "content", Map.of(
                    "user", userField,
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
                    "user", userField,
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
    String audits = get(adminCookie, "/api/v1/audits");
    JsonNode arr = json.readTree(audits);
    assert arr.size() > 0 : "audit trail must not be empty";
  }
}
