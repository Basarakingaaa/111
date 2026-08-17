package com.projectcollab.core.service;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.ProjectMembershipRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.EnumSet;
import java.util.UUID;

@Service
public class AuthorizationService {
    private static final EnumSet<ProjectRole> PROJECT_MANAGERS = EnumSet.of(ProjectRole.OWNER, ProjectRole.MANAGER);
    private static final EnumSet<ProjectRole> RESOURCE_MANAGERS = EnumSet.of(ProjectRole.OWNER, ProjectRole.MANAGER, ProjectRole.OPERATIONS);
    private static final EnumSet<ProjectRole> SECRET_READERS = EnumSet.of(ProjectRole.OWNER, ProjectRole.MANAGER, ProjectRole.OPERATIONS);
    private final ProjectMembershipRepository memberships;
    public AuthorizationService(ProjectMembershipRepository memberships) { this.memberships = memberships; }

    public boolean isSystemAdmin(UserAccount user) {
        return user.systemRole == SystemRole.SUPER_ADMIN || user.systemRole == SystemRole.SYSTEM_ADMIN;
    }

    public void requireProjectMember(UserAccount user, UUID projectId) {
        if (isSystemAdmin(user)) return;
        memberships.findByProjectIdAndUserId(projectId, user.id)
            .orElseThrow(() -> new AccessDeniedException("Project membership required"));
    }

    public void requireProjectManager(UserAccount user, UUID projectId) {
        if (isSystemAdmin(user)) return;
        ProjectRole role = memberships.findByProjectIdAndUserId(projectId, user.id)
            .orElseThrow(() -> new AccessDeniedException("Project membership required")).projectRole;
        if (!PROJECT_MANAGERS.contains(role)) throw new AccessDeniedException("Project manager permission required");
    }

    public void requireSecretReader(UserAccount user, UUID projectId) {
        if (isSystemAdmin(user)) return;
        if (projectId == null) throw new AccessDeniedException("System resource secrets require system administrator permission");
        ProjectRole role = memberships.findByProjectIdAndUserId(projectId, user.id)
            .orElseThrow(() -> new AccessDeniedException("Project membership required")).projectRole;
        if (!SECRET_READERS.contains(role)) throw new AccessDeniedException("Secret reveal permission required");
    }

    public void requireResourceManager(UserAccount user, UUID projectId) {
        if (isSystemAdmin(user)) return;
        ProjectRole role = memberships.findByProjectIdAndUserId(projectId, user.id)
            .orElseThrow(() -> new AccessDeniedException("Project membership required")).projectRole;
        if (!RESOURCE_MANAGERS.contains(role)) throw new AccessDeniedException("Owner, manager, or operations permission required");
    }
}
