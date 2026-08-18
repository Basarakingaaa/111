package com.projectcollab.core.security;

import com.projectcollab.core.domain.AuthType;
import com.projectcollab.core.domain.SystemRole;
import com.projectcollab.core.domain.UserAccount;
import com.projectcollab.core.repo.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserAccountRepository users;
    private final String bootstrapAdminLogin;

    public CustomOAuth2UserService(UserAccountRepository users,
        @Value("${app.bootstrap-admin-github-login}") String bootstrapAdminLogin) {
        this.users = users;
        this.bootstrapAdminLogin = bootstrapAdminLogin;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User github = super.loadUser(request);
        Long githubId = ((Number) github.getAttribute("id")).longValue();
        String login = github.getAttribute("login");
        String name = github.getAttribute("name");
        String email = github.getAttribute("email");
        boolean isBootstrapAdmin = login != null && login.equalsIgnoreCase(bootstrapAdminLogin);

        UserAccount user = users.findByGithubId(githubId).orElseGet(() ->
            new UserAccount(githubId, login, name, email,
                isBootstrapAdmin ? SystemRole.SUPER_ADMIN : SystemRole.PENDING,
                isBootstrapAdmin));
        user.githubLogin = login;
        user.authType = AuthType.GITHUB;
        user.displayName = name;
        user.email = email;
        user.lastLoginAt = Instant.now();
        if (isBootstrapAdmin) {
            user.systemRole = SystemRole.SUPER_ADMIN;
            user.active = true;
        }
        users.save(user);

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_" + user.systemRole.name())),
            github.getAttributes(), "id");
    }
}
