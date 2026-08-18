package com.projectcollab.core.api;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.*;
import com.projectcollab.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final UserAccountRepository users;
    private final CurrentUserService currentUsers;
    private final AuthorizationService authorization;
    private final AuditService audit;

    public ProjectController(ProjectRepository projects, ProjectMembershipRepository memberships,
        UserAccountRepository users, CurrentUserService currentUsers, AuthorizationService authorization, AuditService audit) {
        this.projects = projects; this.memberships = memberships; this.users = users;
        this.currentUsers = currentUsers; this.authorization = authorization; this.audit = audit;
    }

    @GetMapping
    List<ProjectView> list(Authentication authentication) {
        UserAccount actor = currentUsers.require(authentication);
        Set<UUID> allowed = authorization.isSystemAdmin(actor) ? null :
            memberships.findByUserId(actor.id).stream().map(m -> m.projectId).collect(java.util.stream.Collectors.toSet());
        return projects.findByArchivedFalseOrderByName().stream()
            .filter(p -> allowed == null || allowed.contains(p.id)).map(p -> view(p, actor)).toList();
    }

    @PostMapping
    @Transactional
    ProjectView create(@Valid @RequestBody CreateProject request, Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireSystemAdmin(actor);
        Project p = projects.save(new Project(request.name(), request.code().toUpperCase(Locale.ROOT), request.description(), actor.id));
        assignManager(p, request.managerId());
        audit.record(actor.id, "PROJECT_CREATED", "PROJECT", p.id.toString(), "SUCCESS", p.code, http);
        return view(p, actor);
    }

    @PutMapping("/{projectId}")
    @Transactional
    ProjectView update(@PathVariable UUID projectId, @Valid @RequestBody UpdateProject request,
                       Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireSystemAdmin(actor);
        Project project = projects.findById(projectId).orElseThrow();
        project.name = request.name().trim();
        project.description = blankToNull(request.description());
        assignManager(project, request.managerId());
        projects.save(project);
        audit.record(actor.id, "PROJECT_UPDATED", "PROJECT", project.id.toString(), "SUCCESS",
            "manager=" + project.managerId, http);
        return view(project, actor);
    }

    @GetMapping("/{projectId}/members")
    List<MemberView> members(@PathVariable UUID projectId, Authentication authentication) {
        authorization.requireProjectMember(currentUsers.require(authentication), projectId);
        Map<UUID, UserAccount> byId = new HashMap<>(); users.findAllById(memberships.findByProjectId(projectId).stream().map(m -> m.userId).toList())
            .forEach(u -> byId.put(u.id, u));
        return memberships.findByProjectId(projectId).stream().map(m -> {
            UserAccount u = byId.get(m.userId);
            return new MemberView(u.id, u.loginName(), u.displayName, m.projectRole);
        }).toList();
    }

    @PutMapping("/{projectId}/members/{userId}")
    @Transactional
    MemberView setMember(@PathVariable UUID projectId, @PathVariable UUID userId,
                         @Valid @RequestBody SetMember request, Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireSystemAdmin(actor);
        UserAccount member = users.findById(userId).orElseThrow();
        if (!member.active) throw new IllegalArgumentException("Inactive users cannot be assigned to projects");
        ProjectMembership membership = memberships.findByProjectIdAndUserId(projectId, userId)
            .orElseGet(() -> new ProjectMembership(projectId, userId, request.role()));
        membership.projectRole = request.role(); memberships.save(membership);
        audit.record(actor.id, "PROJECT_MEMBER_UPDATED", "PROJECT", projectId.toString(), "SUCCESS",
            "user=" + userId + ",role=" + request.role(), http);
        return new MemberView(member.id, member.loginName(), member.displayName, membership.projectRole);
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @Transactional
    void removeMember(@PathVariable UUID projectId, @PathVariable UUID userId,
                      Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireSystemAdmin(actor);
        Project project = projects.findById(projectId).orElseThrow();
        if (Objects.equals(project.managerId, userId)) {
            throw new IllegalArgumentException("请先为项目指派新的项目管理员，再移除当前管理员");
        }
        ProjectMembership membership = memberships.findByProjectIdAndUserId(projectId, userId).orElseThrow();
        memberships.delete(membership);
        audit.record(actor.id, "PROJECT_MEMBER_REMOVED", "PROJECT", projectId.toString(), "SUCCESS",
            "user=" + userId, http);
    }

    private void assignManager(Project project, UUID managerId) {
        project.managerId = managerId;
        if (managerId == null) return;
        UserAccount manager = users.findById(managerId).orElseThrow();
        if (!manager.active || manager.systemRole == SystemRole.READ_ONLY || manager.systemRole == SystemRole.PENDING) {
            throw new IllegalArgumentException("项目管理员必须是已启用的可工作用户");
        }
        ProjectMembership membership = memberships.findByProjectIdAndUserId(project.id, managerId)
            .orElseGet(() -> new ProjectMembership(project.id, managerId, ProjectRole.MANAGER));
        membership.projectRole = ProjectRole.MANAGER;
        memberships.save(membership);
    }

    private ProjectView view(Project project, UserAccount actor) {
        UserAccount manager = project.managerId == null ? null : users.findById(project.managerId).orElse(null);
        ProjectRole role = memberships.findByProjectIdAndUserId(project.id, actor.id).map(m -> m.projectRole).orElse(null);
        return new ProjectView(project.id, project.name, project.code, project.description, project.managerId,
            manager == null ? null : manager.loginName(), role, authorization.isSystemAdmin(actor),
            authorization.isProjectManager(actor, project.id), authorization.canManageResources(actor, project.id),
            authorization.canReadSecrets(actor, project.id), authorization.canEditWork(actor, project.id));
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record CreateProject(@NotBlank String name, @Pattern(regexp="[A-Za-z0-9_-]{2,64}") String code,
        String description, UUID managerId) {}
    public record UpdateProject(@NotBlank String name, String description, UUID managerId) {}
    public record SetMember(@NotNull ProjectRole role) {}
    public record ProjectView(UUID id, String name, String code, String description, UUID managerId,
        String managerLogin, ProjectRole currentUserRole, boolean canManageProject, boolean canManageTasks,
        boolean canManageResources, boolean canRevealSecrets, boolean canUploadDocuments) {}
    public record MemberView(UUID userId, String loginName, String displayName, ProjectRole role) {}
}
