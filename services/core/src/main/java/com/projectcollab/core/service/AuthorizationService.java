package com.projectcollab.core.service;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.ProjectMembershipRepository;
import com.projectcollab.core.repo.ProjectRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthorizationService {
    private static final EnumSet<ProjectRole> RESOURCE_MANAGERS = EnumSet.of(ProjectRole.OWNER, ProjectRole.MANAGER, ProjectRole.OPERATIONS, ProjectRole.DEVELOPER);
    private static final EnumSet<ProjectRole> SECRET_READERS = EnumSet.of(ProjectRole.OWNER, ProjectRole.MANAGER, ProjectRole.OPERATIONS);
    private final ProjectMembershipRepository memberships;
    private final ProjectRepository projects;
    public AuthorizationService(ProjectMembershipRepository memberships, ProjectRepository projects) {
        this.memberships = memberships;
        this.projects = projects;
    }

    public boolean isSystemAdmin(UserAccount user) {
        return user.systemRole == SystemRole.SUPER_ADMIN || user.systemRole == SystemRole.SYSTEM_ADMIN;
    }

    public void requireSystemAdmin(UserAccount user) {
        if (!isSystemAdmin(user)) throw new AccessDeniedException("System administrator permission required");
    }

    public boolean isProjectManager(UserAccount user, UUID projectId) {
        return projects.findById(projectId).map(project -> Objects.equals(project.managerId, user.id)).orElse(false);
    }

    public void requireProjectMember(UserAccount user, UUID projectId) {
        if (isSystemAdmin(user)) return;
        memberships.findByProjectIdAndUserId(projectId, user.id)
            .orElseThrow(() -> new AccessDeniedException("Project membership required"));
    }

    public void requireProjectManager(UserAccount user, UUID projectId) {
        if (!isProjectManager(user, projectId)) throw new AccessDeniedException("Assigned project manager permission required");
    }

    public void requireTaskEditor(UserAccount user, UUID projectId) {
        requireProjectManager(user, projectId);
    }

    public void requireWorkEditor(UserAccount user, UUID projectId) {
        if (!canEditWork(user, projectId)) throw new AccessDeniedException("Working project membership required");
    }

    public boolean canEditWork(UserAccount user, UUID projectId) {
        if (user.systemRole == SystemRole.READ_ONLY || projectId == null) return false;
        if (isSystemAdmin(user)) return true;
        return memberships.findByProjectIdAndUserId(projectId, user.id)
            .map(membership -> membership.projectRole != ProjectRole.VIEWER).orElse(false);
    }

    public void requireSecretReader(UserAccount user, UUID projectId) {
        if (!canReadSecrets(user, projectId)) throw new AccessDeniedException("Secret reveal permission required");
    }

    public void requireResourceManager(UserAccount user, UUID projectId) {
        if (!canManageResources(user, projectId)) throw new AccessDeniedException("Owner, manager, or operations permission required");
    }

    public boolean canManageResources(UserAccount user, UUID projectId) {
        if (isSystemAdmin(user)) return true;
        if (projectId == null || user.systemRole == SystemRole.READ_ONLY) return false;
        return memberships.findByProjectIdAndUserId(projectId, user.id)
            .map(membership -> RESOURCE_MANAGERS.contains(membership.projectRole)).orElse(false);
    }

    public boolean canReadSecrets(UserAccount user, UUID projectId) {
        if (isSystemAdmin(user)) return true;
        if (projectId == null) return false;
        return memberships.findByProjectIdAndUserId(projectId, user.id)
            .map(membership -> SECRET_READERS.contains(membership.projectRole)).orElse(false);
    }
}
