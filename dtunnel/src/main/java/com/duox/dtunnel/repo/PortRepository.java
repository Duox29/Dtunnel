package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.Port;
import com.duox.dtunnel.domain.PortStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortRepository extends JpaRepository<Port, UUID> {
  List<Port> findByNodeIdAndStatus(UUID nodeId, PortStatus status);
  List<Port> findByOwnerUserIdAndStatusIn(UUID ownerUserId, List<PortStatus> statuses);
  Optional<Port> findByNodeIdAndProtocolAndPortNumberAndStatusIn(UUID nodeId, String protocol, int portNumber, List<PortStatus> statuses);
  long countByNodeIdAndStatus(UUID nodeId, PortStatus status);

  /**
   * detail.md §5: concurrent-safe allocation. FOR UPDATE SKIP LOCKED means two
   * concurrent requests never deadlock — the loser moves to the next candidate.
   * Candidates are ordered so the preferred port is tried first.
   */
  @Query(value = """
      SELECT * FROM ports
       WHERE node_id = :nodeId AND protocol = :protocol
         AND port_number IN (:candidates)
         AND status = 'AVAILABLE'
       ORDER BY array_position(:candidates, port_number)
       LIMIT 1
       FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  Optional<Port> lockFirstAvailable(@Param("nodeId") UUID nodeId,
                                    @Param("protocol") String protocol,
                                    @Param("candidates") List<Integer> candidates);
}
