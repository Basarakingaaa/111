package com.projectcollab.core.repo;

import com.projectcollab.core.domain.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {
    List<ProjectDocument> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    long countByProjectId(UUID projectId);
}
