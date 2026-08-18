package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.Audit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<Audit, Long> {
  Page<Audit> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
