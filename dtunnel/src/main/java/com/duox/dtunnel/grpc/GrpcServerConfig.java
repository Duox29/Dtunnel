package com.duox.dtunnel.grpc;

import com.duox.dtunnel.application.AgentChannelService;
import com.duox.dtunnel.application.AgentEvents;
import com.duox.dtunnel.application.AgentService;
import com.duox.dtunnel.application.DesiredStateService;
import com.duox.dtunnel.grpc.pb.ConfigPush;
import com.duox.dtunnel.repo.AgentRepository;
import com.duox.dtunnel.repo.UserRepository;
import com.duox.dtunnel.security.AgentTokenService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;

/**
 * detail.md §4 Phase 2: starts the gRPC agent channel on its own port
 * (dtunnel.grpc.port, default 9091; 0 = ephemeral, used by tests) and bridges
 * domain events to pushes. Disabled when dtunnel.grpc.enabled=false.
 */
@Configuration
public class GrpcServerConfig {

  private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AgentService agentService;
  private final AgentRepository agents;
  private final AgentTokenService tokens;
  private final DesiredStateService desiredState;
  private final AgentChannelService channel;
  private final GrpcPushService push;
  private final int port;
  private final boolean enabled;

  private Server server;
  private AgentGrpcService service;

  public GrpcServerConfig(UserRepository users, PasswordEncoder encoder, AgentService agentService,
                          AgentRepository agents, AgentTokenService tokens,
                          DesiredStateService desiredState, AgentChannelService channel,
                          GrpcPushService push,
                          @Value("${dtunnel.grpc.port:9091}") int port,
                          @Value("${dtunnel.grpc.enabled:true}") boolean enabled) {
    this.users = users;
    this.encoder = encoder;
    this.agentService = agentService;
    this.agents = agents;
    this.tokens = tokens;
    this.desiredState = desiredState;
    this.channel = channel;
    this.push = push;
    this.port = port;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() throws IOException {
    if (!enabled) {
      log.info("gRPC agent channel disabled (dtunnel.grpc.enabled=false)");
      return;
    }
    service = new AgentGrpcService(users, encoder, agentService, agents, tokens,
        desiredState, channel, push);
    server = ServerBuilder.forPort(port)
        .addService(service)
        .build()
        .start();
    log.info("gRPC agent channel listening on :{}", port);
  }

  @PreDestroy
  public void stop() {
    if (server != null) server.shutdownNow();
  }

  /** Actual bound port (useful with dtunnel.grpc.port=0 in tests). */
  public int boundPort() {
    return server == null ? -1 : server.getPort();
  }

  /** §4: desired-state published → push the new version to the connected agent. */
  @EventListener
  public void onPublished(AgentEvents.DesiredStatePublished e) {
    if (service == null) return;
    ConfigPush cp = service.buildConfigPush(e.agentId());
    push.pushConfig(e.agentId(), cp);
  }

  /** §4: revocation → sub-second push over the open stream. */
  @EventListener
  public void onRevoked(AgentEvents.AgentRevoked e) {
    push.pushRevoked(e.agentId(), "agent revoked by SUPERADMIN");
  }
}
