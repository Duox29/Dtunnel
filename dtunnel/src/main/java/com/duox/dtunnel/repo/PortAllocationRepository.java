package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.PortAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortAllocationRepository extends JpaRepository<PortAllocation, UUID> {
  List<PortAllocation> findByUserId(UUID userId);
  Optional<PortAllocation> findFirstByPortIdOrderByAllocatedAtDesc(UUID portId);

  /** live allocations expiring within the window (5-day warning, detail.md §2) */
  @Query("select pa from PortAllocation pa where pa.graceExpiresAt is null and pa.expiresAt between :from and :to")
  List<PortAllocation> findExpiring(@Param("from") Instant from, @Param("to") Instant to);

  /** expired allocations still without a grace deadline (need service stop + grace start) */
  @Query("select pa from PortAllocation pa where pa.graceExpiresAt is null and pa.expiresAt < :now")
  List<PortAllocation> findExpiredNoGrace(@Param("now") Instant now);

  /** grace period elapsed → release the port */
  @Query("select pa from PortAllocation pa where pa.graceExpiresAt is not null and pa.graceExpiresAt < :now")
  List<PortAllocation> findGraceElapsed(@Param("now") Instant now);
}
