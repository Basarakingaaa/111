package com.projectcollab.core.repo;
import com.projectcollab.core.domain.ManagedResource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ManagedResourceRepository extends JpaRepository<ManagedResource, UUID> {
    List<ManagedResource> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    List<ManagedResource> findByProjectIdIsNullOrderByUpdatedAtDesc();
}

