package com.duox.dtunnel.application;

import com.duox.dtunnel.domain.Node;
import com.duox.dtunnel.repo.NodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * detail.md Milestone 3.3 + §1: usage metering is collected SERVER-SIDE.
 * frpc v0.71 exposes no client traffic API, but every frps node does
 * (GET /api/proxy/{type} → todayTrafficIn/Out per proxy). The control plane
 * polls each node's frps admin API and records counter deltas into
 * usage_records. Counters are per-day on frps, so a decrease means a day
 * rollover or proxy restart — we treat the new value as the delta.
 */
@Component
public class UsageCollectorJob {

  private static final Logger log = LoggerFactory.getLogger(UsageCollectorJob.class);
  /** frps prefixes proxy names with "<user>." — the tunnel id follows "tunnel-". */
  private static final Pattern TUNNEL_ID = Pattern.compile("tunnel-([0-9a-fA-F-]{36})");

  private final NodeRepository nodes;
  private final UsageService usageService;
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3)).build();

  /** last observed cumulative counters per tunnel: [bytesIn, bytesOut]. */
  private final Map<UUID, long[]> lastObserved = new ConcurrentHashMap<>();

  public UsageCollectorJob(NodeRepository nodes, UsageService usageService, ObjectMapper json) {
    this.nodes = nodes;
    this.usageService = usageService;
    this.json = json;
  }

  @Scheduled(fixedDelayString = "${dtunnel.usage.poll-interval:PT60S}")
  @SchedulerLock(name = "usage-collect", lockAtMostFor = "PT55S", lockAtLeastFor = "PT5S")
  public void collect() {
    doCollect();
  }

  /** Lock-free body so tests (and manual triggers) can run it without ShedLock. */
  public void doCollect() {
    for (Node node : nodes.findAll()) {
      String base = node.getFrpsAdminUrl();
      if (base == null || base.isBlank()) continue;
      try {
        collectNode(node, base);
      } catch (Exception e) {
        log.warn("usage collection failed for node {}: {}", node.getCode(), e.toString());
      }
    }
  }

  private void collectNode(Node node, String base) throws Exception {
    for (String type : new String[]{"tcp", "udp", "http"}) {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(base.replaceAll("/$", "") + "/api/proxy/" + type))
          .timeout(Duration.ofSeconds(5))
          .GET().build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200) continue;
      JsonNode proxies = json.readTree(res.body()).path("proxies");
      for (JsonNode p : proxies) {
        String name = p.path("name").asText("");
        Matcher m = TUNNEL_ID.matcher(name);
        if (!m.find()) continue; // not one of our managed tunnels
        UUID tunnelId;
        try {
          tunnelId = UUID.fromString(m.group(1));
        } catch (IllegalArgumentException e) {
          continue;
        }
        long in = p.path("todayTrafficIn").asLong(0);
        long out = p.path("todayTrafficOut").asLong(0);
        long[] last = lastObserved.put(tunnelId, new long[]{in, out});
        long deltaIn = (last == null || in < last[0]) ? in : in - last[0];
        long deltaOut = (last == null || out < last[1]) ? out : out - last[1];
        if (deltaIn > 0 || deltaOut > 0) {
          usageService.recordFromFrps(tunnelId, deltaIn, deltaOut);
          log.info("usage collected: node={} tunnel={} +{}B in / +{}B out",
              node.getCode(), tunnelId, deltaIn, deltaOut);
        }
      }
    }
  }
}
