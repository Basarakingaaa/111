package com.projectcollab.core.repo;

import com.projectcollab.core.domain.ProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, UUID> {
    List<ProjectTask> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    long countByProjectId(UUID projectId);
}
