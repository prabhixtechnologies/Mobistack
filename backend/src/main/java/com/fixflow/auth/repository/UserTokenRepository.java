package com.fixflow.auth.repository;

import com.fixflow.auth.domain.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    Optional<UserToken> findByTokenTypeAndTokenHash(String tokenType, String tokenHash);

    Optional<UserToken> findFirstByTokenTypeAndTokenHashAndEmailIgnoreCaseAndUsedAtIsNull(
            String tokenType, String tokenHash, String email);

    Optional<UserToken> findFirstByTokenTypeAndTokenHashAndPhoneAndUsedAtIsNull(
            String tokenType, String tokenHash, String phone);
}
