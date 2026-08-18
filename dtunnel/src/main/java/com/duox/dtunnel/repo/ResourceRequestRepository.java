package com.duox.dtunnel.repo;

import com.duox.dtunnel.domain.RequestStatus;
import com.duox.dtunnel.domain.ResourceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, UUID> {
  List<ResourceRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);
  List<ResourceRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);
  List<ResourceRequest> findAllByOrderByCreatedAtDesc();
}
