package com.projectcollab.core.repo;

import com.projectcollab.core.domain.ManagedResourceRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ManagedResourceRevisionRepository extends JpaRepository<ManagedResourceRevision, UUID> {
    List<ManagedResourceRevision> findByResourceIdOrderByResourceVersionDesc(UUID resourceId);
}
