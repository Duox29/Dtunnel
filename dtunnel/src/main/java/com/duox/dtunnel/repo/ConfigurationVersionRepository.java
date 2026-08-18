package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.ConfigurationVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConfigurationVersionRepository extends JpaRepository<ConfigurationVersion, UUID> {
  Optional<ConfigurationVersion> findTopByAgentIdOrderByVersionDesc(UUID agentId);

  @Query("select coalesce(max(cv.version), 0) from ConfigurationVersion cv where cv.agentId = :agentId")
  int latestVersion(@Param("agentId") UUID agentId);
}
