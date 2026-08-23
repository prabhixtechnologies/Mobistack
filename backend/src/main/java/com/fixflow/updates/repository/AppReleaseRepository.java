package com.fixflow.updates.repository;

import com.fixflow.updates.domain.AppRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppReleaseRepository extends JpaRepository<AppRelease, UUID> {

    Optional<AppRelease> findByPlatform(String platform);
}
