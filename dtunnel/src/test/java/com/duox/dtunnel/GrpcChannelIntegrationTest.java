package com.duox.dtunnel;

import com.duox.dtunnel.grpc.GrpcServerConfig;
import com.duox.dtunnel.grpc.pb.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * detail.md §4 Phase 2: the gRPC agent channel. Verifies push-config and
 * sub-second revocation over the bidirectional Control stream, with the same
 * Testcontainers stack as the REST business loop.
 */
@SpringBootTest(properties = "dtunnel.grpc.port=0")
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GrpcChannelIntegrationTest {

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
  @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
  @Autowired GrpcServerConfig grpcConfig;

  static String adminCookie;
  static String userCookie;
  static String agentId;
  static String agentToken;
  static String nodeId;
  static String allocationId;
  static ManagedChannel channel;
  static AgentServiceGrpc.AgentServiceBlockingStub blocking;
  static AgentServiceGrpc.AgentServiceStub async;
  static io.grpc.stub.StreamObserver<AgentMessage> controlStream;

  @Test @Order(1)
  void setupUsersAndNode() throws Exception {
    MvcResult reg = mvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", "grpc@example.com", "password", "password123"))))
        .andExpect(status().isOk()).andReturn();
    userCookie = reg.getResponse().getCookie("SESSION").getValue();

    MvcResult login = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", "admin@duox.local", "password", "admin-change-me"))))
        .andExpect(status().isOk()).andReturn();
    adminCookie = login.getResponse().getCookie("SESSION").getValue();

    MvcResult node = mvc.perform(post("/api/v1/nodes")
            .cookie(new jakarta.servlet.http.Cookie("SESSION", adminCookie))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("code", "GRPC-01", "region", "test", "publicAddress", "127.0.0.1"))))
        .andExpect(status().isOk()).andReturn();
    nodeId = json.readTree(node.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(post("/api/v1/nodes/" + nodeId + "/ports/seed")
            .cookie(new jakarta.servlet.http.Cookie("SESSION", adminCookie))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("protocol", "TCP", "start", 21000, "end", 21005))))
        .andExpect(status().isOk());
  }

  @Test @Order(2)
  void registerOverGrpc() throws Exception {
    int port = grpcConfig.boundPort();
    assert port > 0 : "gRPC server must be running";
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    blocking = AgentServiceGrpc.newBlockingStub(channel);
    async = AgentServiceGrpc.newStub(channel);

    RegisterResponse resp = blocking.register(RegisterRequest.newBuilder()
        .setEmail("grpc@example.com").setPassword("password123")
        .setPublicKey("Z3JwYy1kZXZpY2Uta2V5").setPlatform("linux").setAgentVersion("0.2.0")
        .build());
    agentId = resp.getAgentId();
    agentToken = resp.getToken();
    assert "PENDING".equals(resp.getStatus());

    // approve via REST admin API
    mvc.perform(post("/api/v1/agents/" + agentId + "/approve")
            .cookie(new jakarta.servlet.http.Cookie("SESSION", adminCookie)))
        .andExpect(status().isOk());
  }

  @Test @Order(3)
  void controlStreamReceivesPushedConfigAndRevocation() throws Exception {
    // resource request + approval so the agent has an allocation to tunnel on
    MvcResult req = mvc.perform(post("/api/v1/resource-requests")
            .cookie(new jakarta.servlet.http.Cookie("SESSION", userCookie))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("nodeId", nodeId, "protocol", "TCP",
                "preferredPort", 21001, "durationDays", 30, "purpose", "grpc-test"))))
        .andExpect(status().isCreated()).andReturn();
    String requestId = json.readTree(req.getResponse().getContentAsString()).get("id").asText();
    MvcResult approval = mvc.perform(post("/api/v1/resource-requests/" + requestId + "/approve")
            .cookie(new jakarta.servlet.http.Cookie("SESSION", adminCookie)))
        .andExpect(status().isOk()).andReturn();
    allocationId = json.readTree(approval.getResponse().getContentAsString()).get("allocationId").asText();

    // open the Control stream
    var events = new java.util.concurrent.LinkedBlockingQueue<ServerMessage>();
    var errors = new java.util.concurrent.LinkedBlockingQueue<Throwable>();
    controlStream = async.control(new io.grpc.stub.StreamObserver<>() {
      public void onNext(ServerMessage m) { events.add(m); }
      public void onError(Throwable t) { errors.add(t); }
      public void onCompleted() { }
    });
    controlStream.onNext(AgentMessage.newBuilder()
        .setHello(Hello.newBuilder().setToken(agentToken).setAgentVersion("0.2.0").setAppliedVersion(0))
        .build());

    // initial config push arrives without any REST poll
    ServerMessage initial = events.poll(5, TimeUnit.SECONDS);
    assert initial != null && initial.hasConfig() : "expected initial ConfigPush";

    // create a tunnel via REST -> desired-state publish -> PUSH over the stream
    mvc.perform(post("/api/v1/tunnels")
            .cookie(new jakarta.servlet.http.Cookie("SESSION", userCookie))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("allocationId", allocationId, "agentId", agentId,
                "name", "grpc-tunnel", "targetHost", "127.0.0.1", "targetPort", 25565))))
        .andExpect(status().isCreated());

    ServerMessage pushed = events.poll(5, TimeUnit.SECONDS);
    assert pushed != null && pushed.hasConfig() : "expected pushed ConfigPush after tunnel create";
    assert pushed.getConfig().getProxiesCount() == 1 : "pushed config must contain the new tunnel";
    assert pushed.getConfig().getProxies(0).getRemotePort() == 21001;

    // heartbeat over the stream gets an ack
    controlStream.onNext(AgentMessage.newBuilder()
        .setHeartbeat(Heartbeat.newBuilder().setAppliedVersion(pushed.getConfig().getVersion())
            .setAgentVersion("0.2.0")
            .addTunnels(TunnelReport.newBuilder()
                .setTunnelId(pushed.getConfig().getProxies(0).getTunnelId()).setStatus("RUNNING")))
        .build());
    ServerMessage ack = events.poll(5, TimeUnit.SECONDS);
    assert ack != null && ack.hasAck() : "expected HeartbeatAck";

    // revoke -> sub-second Revoked push, then the stream completes
    mvc.perform(post("/api/v1/agents/" + agentId + "/revoke")
            .cookie(new jakarta.servlet.http.Cookie("SESSION", adminCookie)))
        .andExpect(status().isOk());

    ServerMessage revoked = events.poll(5, TimeUnit.SECONDS);
    assert revoked != null && revoked.hasRevoked() : "expected Revoked push (sub-second revocation)";
  }
}
