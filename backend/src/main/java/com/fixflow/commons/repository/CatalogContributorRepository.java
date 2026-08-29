package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogContributor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogContributorRepository extends JpaRepository<CatalogContributor, UUID> {

    List<CatalogContributor> findByTrustedTrue();
}
