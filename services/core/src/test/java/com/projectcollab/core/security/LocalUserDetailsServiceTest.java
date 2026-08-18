package com.projectcollab.core.security;

import com.projectcollab.core.domain.SystemRole;
import com.projectcollab.core.domain.UserAccount;
import com.projectcollab.core.repo.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalUserDetailsServiceTest {
    @Test
    void loadsActiveLocalAccountWithItsSystemRole() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        UserAccount account = UserAccount.local("developer.one", "$2a$12$hash", "Developer One",
            "developer@example.com", SystemRole.STANDARD, true);
        when(users.findByUsernameIgnoreCase("developer.one")).thenReturn(Optional.of(account));

        UserDetails details = new LocalUserDetailsService(users).loadUserByUsername(" developer.one ");

        assertThat(details.getUsername()).isEqualTo("developer.one");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_STANDARD");
    }
}
