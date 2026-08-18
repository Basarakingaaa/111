package com.projectcollab.core.security;

import com.projectcollab.core.domain.AuthType;
import com.projectcollab.core.domain.UserAccount;
import com.projectcollab.core.repo.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LocalUserDetailsService implements UserDetailsService {
    private final UserAccountRepository users;

    public LocalUserDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount user = users.findByUsernameIgnoreCase(username.trim())
            .filter(u -> u.authType == AuthType.LOCAL && u.passwordHash != null)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
        return User.withUsername(user.username)
            .password(user.passwordHash)
            .authorities("ROLE_" + user.systemRole.name())
            .disabled(!user.active)
            .build();
    }
}
