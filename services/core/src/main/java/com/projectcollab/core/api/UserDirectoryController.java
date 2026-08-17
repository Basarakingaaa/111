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
            .sorted(Comparator.comparing(u -> u.githubLogin.toLowerCase()))
            .map(u -> new DirectoryUser(u.id, u.githubLogin, u.displayName, u.email, u.systemRole.name(), true)).toList();
    }
    record DirectoryUser(UUID id, String githubLogin, String displayName, String email, String systemRole, boolean active) {}
}
