package com.projectcollab.core.api;

import com.projectcollab.core.domain.UserAccount;
import com.projectcollab.core.service.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SessionController {
    private final CurrentUserService currentUsers;
    public SessionController(CurrentUserService currentUsers) { this.currentUsers = currentUsers; }

    @GetMapping("/me")
    Me me(Authentication authentication) {
        UserAccount u = currentUsers.require(authentication);
        return new Me(u.id.toString(), u.loginName(), u.authType.name(), u.displayName, u.email, u.systemRole.name(), u.active);
    }

    @GetMapping("/csrf")
    Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    record Me(String id, String loginName, String authType, String displayName, String email, String systemRole, boolean active) {}
}
