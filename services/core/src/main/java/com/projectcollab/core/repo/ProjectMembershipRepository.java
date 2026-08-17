package com.projectcollab.core.repo;
import com.projectcollab.core.domain.ProjectMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {
    Optional<ProjectMembership> findByProjectIdAndUserId(UUID projectId, UUID userId);
    List<ProjectMembership> findByUserId(UUID userId);
    List<ProjectMembership> findByProjectId(UUID projectId);
}

