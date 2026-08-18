package com.duox.dtunnel.grpc;

import com.duox.dtunnel.grpc.pb.ConfigPush;
import com.duox.dtunnel.grpc.pb.Revoked;
import com.duox.dtunnel.grpc.pb.ServerMessage;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * detail.md §4 Phase 2: registry of open agent Control streams. The control
 * plane pushes desired-state versions and revocation signals through these
 * streams the moment they happen — this is what bounds revocation latency to
 * milliseconds instead of the heartbeat interval.
 */
@Component
public class GrpcPushService {

  private static final Logger log = LoggerFactory.getLogger(GrpcPushService.class);

  private final Map<UUID, StreamObserver<ServerMessage>> streams = new ConcurrentHashMap<>();

  public void register(UUID agentId, StreamObserver<ServerMessage> stream) {
    StreamObserver<ServerMessage> old = streams.put(agentId, stream);
    if (old != null) {
      try { old.onCompleted(); } catch (RuntimeException ignored) { }
    }
    log.info("grpc control stream opened for agent {}", agentId);
  }

  public void unregister(UUID agentId, StreamObserver<ServerMessage> stream) {
    if (streams.remove(agentId, stream)) {
      log.info("grpc control stream closed for agent {}", agentId);
    }
  }

  /** Push a new desired-state version to the agent's open stream, if any. */
  public void pushConfig(UUID agentId, ConfigPush config) {
    StreamObserver<ServerMessage> s = streams.get(agentId);
    if (s == null) return; // agent is REST-polling; it will pick it up on poll
    try {
      synchronized (s) {
        s.onNext(ServerMessage.newBuilder().setConfig(config).build());
      }
      log.info("grpc config push: agent {} version {}", agentId, config.getVersion());
    } catch (RuntimeException e) {
      log.warn("grpc config push failed for agent {}: {}", agentId, e.toString());
      streams.remove(agentId, s);
    }
  }

  /** Sub-second revocation (§4): tell the agent immediately, don't wait for poll. */
  public void pushRevoked(UUID agentId, String reason) {
    StreamObserver<ServerMessage> s = streams.get(agentId);
    if (s == null) return;
    try {
      synchronized (s) {
        s.onNext(ServerMessage.newBuilder()
            .setRevoked(Revoked.newBuilder().setReason(reason == null ? "revoked" : reason))
            .build());
        s.onCompleted();
      }
      log.info("grpc revocation push: agent {}", agentId);
    } catch (RuntimeException e) {
      log.warn("grpc revocation push failed for agent {}: {}", agentId, e.toString());
    } finally {
      streams.remove(agentId, s);
    }
  }

  public int connectedStreams() {
    return streams.size();
  }
}
