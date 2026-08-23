package com.fixflow.flags.repository;

import com.fixflow.flags.domain.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    List<FeatureFlag> findByShopIdIsNull();

    List<FeatureFlag> findByShopId(UUID shopId);

    Optional<FeatureFlag> findByShopIdIsNullAndCode(String code);

    Optional<FeatureFlag> findByShopIdAndCode(UUID shopId, String code);
}
