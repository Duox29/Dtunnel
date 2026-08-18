package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.UsageDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UsageDailyRepository extends JpaRepository<UsageDaily, UsageDaily.Key> {

  List<UsageDaily> findByTunnelIdOrderByDayDesc(UUID tunnelId);

  /** Idempotent upsert: recompute wins over any previous partial value. */
  @Modifying
  @Query(value = """
      INSERT INTO usage_daily (tunnel_id, day, bytes_in, bytes_out)
      VALUES (:tunnelId, :day, :bytesIn, :bytesOut)
      ON CONFLICT (tunnel_id, day)
      DO UPDATE SET bytes_in = EXCLUDED.bytes_in, bytes_out = EXCLUDED.bytes_out
      """, nativeQuery = true)
  void upsert(@Param("tunnelId") UUID tunnelId, @Param("day") LocalDate day,
              @Param("bytesIn") long bytesIn, @Param("bytesOut") long bytesOut);
}
