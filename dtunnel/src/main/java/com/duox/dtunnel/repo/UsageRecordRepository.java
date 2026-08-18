package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {
  List<UsageRecord> findByTunnelId(UUID tunnelId);

  @Query("select coalesce(sum(u.bytesIn), 0) from UsageRecord u where u.tunnelId = :tunnelId")
  long totalBytesIn(@Param("tunnelId") UUID tunnelId);

  @Query("select coalesce(sum(u.bytesOut), 0) from UsageRecord u where u.tunnelId = :tunnelId")
  long totalBytesOut(@Param("tunnelId") UUID tunnelId);
}
