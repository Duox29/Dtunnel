package com.duox.dtunnel.application;

import com.duox.dtunnel.repo.UsageDailyRepository;
import com.duox.dtunnel.repo.UsageRecordRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * detail.md §10 aggregateUsage(): roll hourly usage_records up into daily
 * totals (usage_daily). Cluster-safe via ShedLock; idempotent — each run
 * recomputes the last two days (today is still accumulating), so the upsert
 * converges rather than double-counts.
 */
@Component
public class UsageAggregateJob {

  private static final Logger log = LoggerFactory.getLogger(UsageAggregateJob.class);

  private final UsageRecordRepository records;
  private final UsageDailyRepository daily;

  public UsageAggregateJob(UsageRecordRepository records, UsageDailyRepository daily) {
    this.records = records;
    this.daily = daily;
  }

  @Scheduled(cron = "${dtunnel.usage.aggregate-cron:0 5 * * * *}") // 5 min past every hour
  @SchedulerLock(name = "usage-aggregate", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
  public void aggregate() {
    doAggregate();
  }

  /** Lock-free body so tests can drive it directly. */
  @Transactional
  public void doAggregate() {
    Instant since = LocalDate.now().minusDays(1).atStartOfDay()
        .atZone(java.time.ZoneOffset.UTC).toInstant();
    int rows = 0;
    for (Object[] r : records.dailySumsSince(since)) {
      UUID tunnelId = (UUID) r[0];
      // pgjdbc maps DATE to java.time.LocalDate (java.sql.Date on older drivers)
      LocalDate day = r[1] instanceof LocalDate ld ? ld
          : java.sql.Date.class.isInstance(r[1]) ? ((java.sql.Date) r[1]).toLocalDate()
          : LocalDate.parse(r[1].toString());
      long bytesIn = ((Number) r[2]).longValue();
      long bytesOut = ((Number) r[3]).longValue();
      daily.upsert(tunnelId, day, bytesIn, bytesOut);
      rows++;
    }
    if (rows > 0) log.info("usage aggregate: upserted {} daily rows", rows);
  }
}
