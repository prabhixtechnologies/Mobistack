package com.fixflow.auth.repository;

import com.fixflow.auth.domain.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndSubject(String provider, String subject);
}
