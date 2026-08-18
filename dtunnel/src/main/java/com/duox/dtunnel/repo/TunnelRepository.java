package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.Tunnel;
import com.duox.dtunnel.domain.TunnelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TunnelRepository extends JpaRepository<Tunnel, UUID> {
  List<Tunnel> findByUserId(UUID userId);
  List<Tunnel> findByAgentId(UUID agentId);
  List<Tunnel> findByPortAllocationId(UUID portAllocationId);
  List<Tunnel> findByStatus(TunnelStatus status);
  List<Tunnel> findByStatusIn(List<TunnelStatus> statuses);
  java.util.Optional<Tunnel> findByDomain(String domain);
}
