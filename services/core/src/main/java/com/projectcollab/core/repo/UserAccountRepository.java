package com.projectcollab.core.repo;
import com.projectcollab.core.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByGithubId(Long githubId);
    Optional<UserAccount> findByGithubLoginIgnoreCase(String githubLogin);
}

