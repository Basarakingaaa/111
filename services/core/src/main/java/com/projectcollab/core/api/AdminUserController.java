package com.projectcollab.core.api;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.UserAccountRepository;
import com.projectcollab.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserAccountRepository users;
    private final CurrentUserService currentUsers;
    private final AuditService audit;

    public AdminUserController(UserAccountRepository users, CurrentUserService currentUsers, AuditService audit) {
        this.users = users; this.currentUsers = currentUsers; this.audit = audit;
    }

    @GetMapping
    List<UserView> list() {
        return users.findAll().stream().sorted(Comparator.comparing(u -> u.githubLogin.toLowerCase()))
            .map(UserView::from).toList();
    }

    @PatchMapping("/{id}")
    @Transactional
    UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateUser request,
                    Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        UserAccount target = users.findById(id).orElseThrow();
        if (actor.systemRole != SystemRole.SUPER_ADMIN &&
            (target.systemRole == SystemRole.SUPER_ADMIN || request.systemRole() == SystemRole.SUPER_ADMIN)) {
            throw new org.springframework.security.access.AccessDeniedException("Only a super administrator can manage super administrators");
        }
        if (actor.id.equals(target.id) && (!request.active() || request.systemRole() != actor.systemRole)) {
            throw new IllegalArgumentException("Administrators cannot deactivate or change their own role");
        }
        target.systemRole = request.systemRole(); target.active = request.active();
        users.save(target);
        audit.record(actor.id, "USER_ACCESS_UPDATED", "USER", id.toString(), "SUCCESS",
            "role=" + request.systemRole() + ",active=" + request.active(), http);
        return UserView.from(target);
    }

    public record UpdateUser(@NotNull SystemRole systemRole, boolean active) {}
    public record UserView(UUID id, String githubLogin, String displayName, String email,
                           SystemRole systemRole, boolean active) {
        static UserView from(UserAccount u) { return new UserView(u.id, u.githubLogin, u.displayName, u.email, u.systemRole, u.active); }
    }
}
