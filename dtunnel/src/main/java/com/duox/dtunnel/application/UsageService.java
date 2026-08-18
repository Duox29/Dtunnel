package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.Tunnel;
import com.duox.dtunnel.domain.UsageRecord;
import com.duox.dtunnel.repo.TunnelRepository;
import com.duox.dtunnel.repo.UsageRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * detail.md Milestone 3.3: usage metering. Agents report sampled counters
 * (bytes in/out from frpc/frps traffic stats); the control plane buckets them
 * into usage_records for later roll-up and quota enforcement.
 */
@Service
public class UsageService {

  private final UsageRecordRepository usage;
  private final TunnelRepository tunnels;

  public UsageService(UsageRecordRepository usage, TunnelRepository tunnels) {
    this.usage = usage;
    this.tunnels = tunnels;
  }

  @Transactional
  public UsageRecord record(UUID agentId, UUID tunnelId, long bytesIn, long bytesOut, int activeSeconds) {
    Tunnel t = tunnels.findById(tunnelId)
        .orElseThrow(() -> ApiException.notFound("tunnel"));
    if (!t.getAgentId().equals(agentId)) throw ApiException.forbidden("tunnel belongs to another agent");
    if (bytesIn < 0 || bytesOut < 0 || activeSeconds < 0) throw ApiException.badRequest("negative usage");

    UsageRecord u = new UsageRecord();
    u.setTunnelId(tunnelId);
    u.setBytesIn(bytesIn);
    u.setBytesOut(bytesOut);
    u.setActiveSeconds(activeSeconds);
    // bucket to the current hour for cheap roll-up
    u.setBucketStart(Instant.now().truncatedTo(ChronoUnit.HOURS));
    return usage.save(u);
  }

  public long bytesIn(UUID tunnelId) { return usage.totalBytesIn(tunnelId); }
  public long bytesOut(UUID tunnelId) { return usage.totalBytesOut(tunnelId); }
}
