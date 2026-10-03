package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogEntities.CatalogComponent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CatalogComponentRepository extends JpaRepository<CatalogComponent, UUID> {

    @Query("""
            select c from CatalogComponent c
            where c.groupId = :groupId
              and c.categoryCode = :categoryCode
              and lower(c.name) = lower(:name)
            """)
    Optional<CatalogComponent> findByIdentity(@Param("groupId") UUID groupId,
                                              @Param("categoryCode") String categoryCode,
                                              @Param("name") String name);

    @Query("""
            select c from CatalogComponent c
            where c.groupId = :groupId
              and lower(c.name) like lower(concat('%', :term, '%'))
            order by c.name asc
            """)
    Page<CatalogComponent> search(@Param("groupId") UUID groupId, @Param("term") String term, Pageable pageable);

    Page<CatalogComponent> findByGroupId(UUID groupId, Pageable pageable);

    long countByGroupId(UUID groupId);

    Optional<CatalogComponent> findByIdAndGroupId(UUID id, UUID groupId);
}
