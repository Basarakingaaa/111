package com.projectcollab.core.repo;

import com.projectcollab.core.domain.DocumentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, UUID> {
    List<DocumentRequest> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    List<DocumentRequest> findByTaskIdOrderByUpdatedAtDesc(UUID taskId);
    long countByTaskId(UUID taskId);
    long countByProjectId(UUID projectId);
}
