package com.projectcollab.core.service;

import com.projectcollab.core.domain.UserAccount;
import com.projectcollab.core.repo.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserAccountRepository users;
    public CurrentUserService(UserAccountRepository users) { this.users = users; }

    public UserAccount require(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Authenticated user required");
        }
        if (authentication.getPrincipal() instanceof OAuth2User oauth) {
            Long githubId = ((Number) oauth.getAttribute("id")).longValue();
            return users.findByGithubId(githubId).orElseThrow();
        }
        if (authentication.getPrincipal() instanceof UserDetails local) {
            return users.findByUsernameIgnoreCase(local.getUsername()).orElseThrow();
        }
        throw new IllegalStateException("Unsupported authenticated principal");
    }
}
