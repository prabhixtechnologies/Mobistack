package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogEntities.CatalogBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Brands in the commons.
 *
 * <p>Not one query in this package filters by shop, which is the difference from every other
 * repository here. That is not an omission: these tables have no shop column, because the facts in
 * them are true for everyone.
 */
public interface CatalogBrandRepository extends JpaRepository<CatalogBrand, UUID> {

    @Query("select b from CatalogBrand b where lower(b.name) = lower(:name)")
    Optional<CatalogBrand> findByName(@Param("name") String name);

    List<CatalogBrand> findAllByOrderByNameAsc();
}
