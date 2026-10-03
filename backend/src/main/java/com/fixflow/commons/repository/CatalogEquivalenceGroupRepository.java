package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogEntities.CatalogEquivalenceGroup;
import com.fixflow.commons.domain.CatalogEntities.CatalogEquivalenceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogEquivalenceGroupRepository extends JpaRepository<CatalogEquivalenceGroup, UUID> {

    @Query("""
            select g from CatalogEquivalenceGroup g
            where g.groupId = :groupId
              and g.categoryCode = :categoryCode
              and lower(g.name) = lower(:name)
            """)
    Optional<CatalogEquivalenceGroup> findByIdentity(@Param("groupId") UUID groupId,
                                                     @Param("categoryCode") String categoryCode,
                                                     @Param("name") String name);

    @Query("""
            select g from CatalogEquivalenceGroup g
            where g.groupId = :groupId
              and g.id in (
                  select m.equivalenceGroupId from CatalogEquivalenceMember m
                  where m.deviceId = :deviceId
              )
            order by g.categoryCode asc, g.name asc
            """)
    List<CatalogEquivalenceGroup> findContainingDevice(@Param("groupId") UUID groupId,
                                                       @Param("deviceId") UUID deviceId);
}
