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
            .filter(p -> allowed == null || allowed.contains(p.id)).map(ProjectView::from).toList();
    }

    @PostMapping
    @Transactional
    ProjectView create(@Valid @RequestBody CreateProject request, Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        if (actor.systemRole == SystemRole.READ_ONLY) throw new org.springframework.security.access.AccessDeniedException("Read-only users cannot create projects");
        Project p = projects.save(new Project(request.name(), request.code().toUpperCase(Locale.ROOT), request.description(), actor.id));
        memberships.save(new ProjectMembership(p.id, actor.id, ProjectRole.OWNER));
        audit.record(actor.id, "PROJECT_CREATED", "PROJECT", p.id.toString(), "SUCCESS", p.code, http);
        return ProjectView.from(p);
    }

    @GetMapping("/{projectId}/members")
    List<MemberView> members(@PathVariable UUID projectId, Authentication authentication) {
        authorization.requireProjectMember(currentUsers.require(authentication), projectId);
        Map<UUID, UserAccount> byId = new HashMap<>(); users.findAllById(memberships.findByProjectId(projectId).stream().map(m -> m.userId).toList())
            .forEach(u -> byId.put(u.id, u));
        return memberships.findByProjectId(projectId).stream().map(m -> {
            UserAccount u = byId.get(m.userId);
            return new MemberView(u.id, u.githubLogin, u.displayName, m.projectRole);
        }).toList();
    }

    @PutMapping("/{projectId}/members/{userId}")
    @Transactional
    MemberView setMember(@PathVariable UUID projectId, @PathVariable UUID userId,
                         @Valid @RequestBody SetMember request, Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireProjectManager(actor, projectId);
        UserAccount member = users.findById(userId).orElseThrow();
        if (!member.active) throw new IllegalArgumentException("Inactive users cannot be assigned to projects");
        ProjectMembership membership = memberships.findByProjectIdAndUserId(projectId, userId)
            .orElseGet(() -> new ProjectMembership(projectId, userId, request.role()));
        membership.projectRole = request.role(); memberships.save(membership);
        audit.record(actor.id, "PROJECT_MEMBER_UPDATED", "PROJECT", projectId.toString(), "SUCCESS",
            "user=" + userId + ",role=" + request.role(), http);
        return new MemberView(member.id, member.githubLogin, member.displayName, membership.projectRole);
    }

    public record CreateProject(@NotBlank String name, @Pattern(regexp="[A-Za-z0-9_-]{2,64}") String code, String description) {}
    public record SetMember(@NotNull ProjectRole role) {}
    public record ProjectView(UUID id, String name, String code, String description) {
        static ProjectView from(Project p) { return new ProjectView(p.id, p.name, p.code, p.description); }
    }
    public record MemberView(UUID userId, String githubLogin, String displayName, ProjectRole role) {}
}

