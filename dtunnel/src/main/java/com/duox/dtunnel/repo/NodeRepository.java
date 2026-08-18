package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.Node;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface NodeRepository extends JpaRepository<Node, UUID> {
  Optional<Node> findByCode(String code);
}
