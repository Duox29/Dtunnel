package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.Agent;
import com.duox.dtunnel.domain.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<Agent, UUID> {
  Optional<Agent> findByPublicKey(String publicKey);
  List<Agent> findByUserId(UUID userId);
  long countByUserIdAndStatusNot(UUID userId, AgentStatus status);

  @Query("select a from Agent a where a.status = com.duox.dtunnel.domain.AgentStatus.ONLINE and (a.lastSeenAt is null or a.lastSeenAt < :cutoff)")
  List<Agent> findStaleOnline(@Param("cutoff") Instant cutoff);
}
