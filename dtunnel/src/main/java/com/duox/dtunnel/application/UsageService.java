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
  private final com.duox.dtunnel.repo.UsageDailyRepository dailyRepo;

  public UsageService(UsageRecordRepository usage, TunnelRepository tunnels,
                      com.duox.dtunnel.repo.UsageDailyRepository dailyRepo) {
    this.usage = usage;
    this.tunnels = tunnels;
    this.dailyRepo = dailyRepo;
  }

  /**
   * Server-side collection (authoritative, detail.md §1): the control plane
   * reads per-proxy traffic counters from the node's frps admin API and
   * records the delta. No agent involvement.
   */
  @Transactional
  public UsageRecord recordFromFrps(UUID tunnelId, long bytesIn, long bytesOut) {
    Tunnel t = tunnels.findById(tunnelId)
        .orElseThrow(() -> ApiException.notFound("tunnel"));
    if (bytesIn < 0 || bytesOut < 0) return null;
    UsageRecord u = new UsageRecord();
    u.setTunnelId(tunnelId);
    u.setBytesIn(bytesIn);
    u.setBytesOut(bytesOut);
    u.setActiveSeconds(0);
    u.setBucketStart(Instant.now().truncatedTo(ChronoUnit.HOURS));
    return usage.save(u);
  }

  public long bytesIn(UUID tunnelId) { return usage.totalBytesIn(tunnelId); }
  public long bytesOut(UUID tunnelId) { return usage.totalBytesOut(tunnelId); }

  /** Daily rollup (usage_daily, §10 aggregateUsage), newest first, capped at `days`. */
  public java.util.List<java.util.Map<String, Object>> history(UUID tunnelId, int days) {
    return dailyRepo.findByTunnelIdOrderByDayDesc(tunnelId).stream()
        .limit(days)
        .map(d -> {
          java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
          m.put("day", d.getDay().toString());
          m.put("bytesIn", d.getBytesIn());
          m.put("bytesOut", d.getBytesOut());
          return m;
        })
        .toList();
  }
}
