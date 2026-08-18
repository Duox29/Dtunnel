package com.duox.dtunnel.grpc;

import com.duox.dtunnel.application.AgentChannelService;
import com.duox.dtunnel.application.AgentService;
import com.duox.dtunnel.application.DesiredStateService;
import com.duox.dtunnel.domain.Agent;
import com.duox.dtunnel.domain.AgentStatus;
import com.duox.dtunnel.domain.User;
import com.duox.dtunnel.grpc.pb.*;
import com.duox.dtunnel.repo.AgentRepository;
import com.duox.dtunnel.repo.UserRepository;
import com.duox.dtunnel.security.AgentTokenService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * detail.md §4 Phase 2: gRPC agent channel. Register mirrors the REST
 * contract; the Control stream is the steady-state channel carrying
 * desired-state pushes, sub-second revocation, and heartbeats.
 */
public class AgentGrpcService extends AgentServiceGrpc.AgentServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(AgentGrpcService.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AgentService agentService;
  private final AgentRepository agents;
  private final AgentTokenService tokens;
  private final DesiredStateService desiredState;
  private final AgentChannelService channel;
  private final GrpcPushService push;

  public AgentGrpcService(UserRepository users, PasswordEncoder encoder, AgentService agentService,
                          AgentRepository agents, AgentTokenService tokens,
                          DesiredStateService desiredState, AgentChannelService channel,
                          GrpcPushService push) {
    this.users = users;
    this.encoder = encoder;
    this.agentService = agentService;
    this.agents = agents;
    this.tokens = tokens;
    this.desiredState = desiredState;
    this.channel = channel;
    this.push = push;
  }

  @Override
  public void register(RegisterRequest req, StreamObserver<RegisterResponse> obs) {
    try {
      User u = users.findByEmailIgnoreCase(req.getEmail().trim()).orElse(null);
      if (u == null || u.getPasswordHash() == null || !encoder.matches(req.getPassword(), u.getPasswordHash())) {
        obs.onError(Status.UNAUTHENTICATED.withDescription("invalid credentials").asRuntimeException());
        return;
      }
      Agent a = agentService.register(u.getId(), req.getPublicKey(), req.getPlatform(), req.getAgentVersion());
      String token = tokens.issue(a);
      obs.onNext(RegisterResponse.newBuilder()
          .setAgentId(a.getId().toString())
          .setToken(token)
          .setStatus(a.getStatus().name())
          .build());
      obs.onCompleted();
    } catch (RuntimeException e) {
      obs.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public StreamObserver<AgentMessage> control(StreamObserver<ServerMessage> serverOut) {
    return new StreamObserver<>() {
      private UUID agentId;

      @Override
      public void onNext(AgentMessage msg) {
        switch (msg.getKindCase()) {
          case HELLO -> handleHello(msg.getHello(), serverOut);
          case HEARTBEAT -> handleHeartbeat(msg.getHeartbeat(), serverOut);
          default -> { }
        }
      }

      private void handleHello(Hello hello, StreamObserver<ServerMessage> out) {
        UUID resolved = tokens.resolve(hello.getToken());
        if (resolved == null) {
          out.onError(Status.UNAUTHENTICATED.withDescription("bad device token").asRuntimeException());
          return;
        }
        Agent a = agents.findById(resolved).orElse(null);
        if (a == null || a.getStatus() == AgentStatus.REVOKED) {
          out.onError(Status.PERMISSION_DENIED.withDescription("agent revoked").asRuntimeException());
          return;
        }
        agentId = resolved;
        push.register(agentId, out);
        // immediate desired-state push so the agent converges without a poll
        push.pushConfig(agentId, buildConfigPush(agentId));
      }

      private void handleHeartbeat(Heartbeat hb, StreamObserver<ServerMessage> out) {
        if (agentId == null) {
          out.onError(Status.FAILED_PRECONDITION.withDescription("hello first").asRuntimeException());
          return;
        }
        try {
          List<AgentChannelService.TunnelReport> reports = hb.getTunnelsList().stream()
              .map(t -> new AgentChannelService.TunnelReport(t.getTunnelId(), t.getStatus()))
              .toList();
          int desiredVersion = channel.heartbeat(agentId, hb.getAppliedVersion(),
              hb.getAgentVersion().isEmpty() ? null : hb.getAgentVersion(), reports);
          synchronized (out) {
            out.onNext(ServerMessage.newBuilder()
                .setAck(HeartbeatAck.newBuilder().setDesiredVersion(desiredVersion))
                .build());
          }
        } catch (RuntimeException e) {
          log.warn("grpc heartbeat failed for agent {}: {}", agentId, e.toString());
        }
      }

      @Override
      public void onError(Throwable t) {
        if (agentId != null) push.unregister(agentId, serverOut);
      }

      @Override
      public void onCompleted() {
        if (agentId != null) push.unregister(agentId, serverOut);
        serverOut.onCompleted();
      }
    };
  }

  /** Convert the stored desired-state payload into a protobuf ConfigPush. */
  ConfigPush buildConfigPush(UUID agentId) {
    Map<String, Object> cfg = desiredState.storedConfig(agentId);
    ConfigPush.Builder b = ConfigPush.newBuilder()
        .setVersion(cfg.get("version") instanceof Number n ? n.intValue() : 0);
    Object payload = cfg.get("payload");
    if (payload instanceof Map<?, ?> p && p.get("proxies") instanceof List<?> proxies) {
      for (Object o : proxies) {
        if (!(o instanceof Map<?, ?> m)) continue;
        Proxy.Builder pb = Proxy.newBuilder()
            .setTunnelId(str(m.get("tunnelId")))
            .setName(str(m.get("name")))
            .setType(str(m.get("type")))
            .setServerAddr(str(m.get("serverAddr")))
            .setServerPort(num(m.get("serverPort")))
            .setRemotePort(num(m.get("remotePort")))
            .setDomain(str(m.get("domain")))
            .setLocalHost(str(m.get("localHost")))
            .setLocalPort(num(m.get("localPort")))
            .setBandwidthLimitMbps(num(m.get("bandwidthLimitMbps")));
        b.addProxies(pb);
      }
    }
    return b.build();
  }

  private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
  private static int num(Object o) { return o instanceof Number n ? n.intValue() : 0; }
}
