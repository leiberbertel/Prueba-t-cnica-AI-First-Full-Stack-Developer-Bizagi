package dev.leiber.polla.auth.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.leiber.polla.auth.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserAccount> findByIdAndDeletedAtIsNull(long id);

    boolean existsByIdAndDeletedAtIsNull(long id);
}
