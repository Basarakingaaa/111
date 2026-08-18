package com.projectcollab.core.api;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.UserAccountRepository;
import com.projectcollab.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserAccountRepository users;
    private final CurrentUserService currentUsers;
    private final AuditService audit;
    private final PasswordEncoder passwords;

    public AdminUserController(UserAccountRepository users, CurrentUserService currentUsers,
                               AuditService audit, PasswordEncoder passwords) {
        this.users = users; this.currentUsers = currentUsers; this.audit = audit; this.passwords = passwords;
    }

    @GetMapping
    List<UserView> list() {
        return users.findAll().stream().sorted(Comparator.comparing(u -> u.loginName().toLowerCase()))
            .map(UserView::from).toList();
    }

    @PostMapping
    @Transactional
    UserView create(@Valid @RequestBody CreateLocalUser request,
                    Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        if (actor.systemRole != SystemRole.SUPER_ADMIN && request.systemRole() == SystemRole.SUPER_ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Only a super administrator can create super administrators");
        }
        String username = request.username().trim();
        if (users.findByUsernameIgnoreCase(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        UserAccount created = users.save(UserAccount.local(username, passwords.encode(request.password()),
            blankToNull(request.displayName()), blankToNull(request.email()), request.systemRole(), request.active()));
        audit.record(actor.id, "LOCAL_USER_CREATED", "USER", created.id.toString(), "SUCCESS",
            "username=" + created.username + ",role=" + created.systemRole + ",active=" + created.active, http);
        return UserView.from(created);
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

    @PutMapping("/{id}/password")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Transactional
    void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPassword request,
                       Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        UserAccount target = users.findById(id).orElseThrow();
        if (target.authType != AuthType.LOCAL) {
            throw new IllegalArgumentException("GitHub accounts do not have a local password");
        }
        if (actor.systemRole != SystemRole.SUPER_ADMIN && target.systemRole == SystemRole.SUPER_ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Only a super administrator can reset a super administrator password");
        }
        target.passwordHash = passwords.encode(request.password());
        users.save(target);
        audit.record(actor.id, "LOCAL_PASSWORD_RESET", "USER", id.toString(), "SUCCESS", null, http);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateLocalUser(
        @NotBlank @Pattern(regexp="[A-Za-z0-9._-]{3,64}") String username,
        @NotBlank @Size(min=10, max=128) String password,
        @Size(max=255) String displayName,
        @Email @Size(max=320) String email,
        @NotNull SystemRole systemRole,
        boolean active) {}
    public record UpdateUser(@NotNull SystemRole systemRole, boolean active) {}
    public record ResetPassword(@NotBlank @Size(min=10, max=128) String password) {}
    public record UserView(UUID id, String loginName, AuthType authType, String displayName, String email,
                           SystemRole systemRole, boolean active) {
        static UserView from(UserAccount u) {
            return new UserView(u.id, u.loginName(), u.authType, u.displayName, u.email, u.systemRole, u.active);
        }
    }
}
