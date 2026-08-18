package com.projectcollab.core.api;

import com.projectcollab.core.repo.UserAccountRepository;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserDirectoryController {
    private final UserAccountRepository users;
    public UserDirectoryController(UserAccountRepository users) { this.users = users; }

    @GetMapping("/directory")
    List<DirectoryUser> directory() {
        return users.findAll().stream().filter(u -> u.active)
            .sorted(Comparator.comparing(u -> u.loginName().toLowerCase()))
            .map(u -> new DirectoryUser(u.id, u.loginName(), u.authType.name(), u.displayName, u.email, u.systemRole.name(), true)).toList();
    }
    record DirectoryUser(UUID id, String loginName, String authType, String displayName, String email, String systemRole, boolean active) {}
}
