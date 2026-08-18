package com.projectcollab.core.repo;
import com.projectcollab.core.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByArchivedFalseOrderByName();
    List<Project> findByManagerIdAndArchivedFalseOrderByName(UUID managerId);
}
