package com.projectcollab.core.repo;
import com.projectcollab.core.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {}

