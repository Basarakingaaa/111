package com.projectcollab.core.service;

import com.projectcollab.core.domain.AuditEvent;
import com.projectcollab.core.repo.AuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository events;
    public AuditService(AuditEventRepository events) { this.events = events; }
    public void record(UUID actorId, String action, String type, String id, String outcome, String details, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        events.save(new AuditEvent(actorId, action, type, id, outcome, details, ip));
    }
}

