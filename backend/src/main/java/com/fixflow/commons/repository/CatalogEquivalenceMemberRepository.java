package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogEntities.CatalogEquivalenceGroup;
import com.fixflow.commons.domain.CatalogEntities.CatalogEquivalenceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogEquivalenceMemberRepository extends JpaRepository<CatalogEquivalenceMember, UUID> {

    List<CatalogEquivalenceMember> findByEquivalenceGroupId(UUID equivalenceGroupId);

    @Query("""
            select m from CatalogEquivalenceMember m
            where m.deviceId = :deviceId
              and m.equivalenceGroupId in (
                  select g.id from CatalogEquivalenceGroup g
                  where g.groupId = :groupId and g.categoryCode = :categoryCode
              )
            """)
    Optional<CatalogEquivalenceMember> findMembership(@Param("groupId") UUID groupId,
                                                      @Param("categoryCode") String categoryCode,
                                                      @Param("deviceId") UUID deviceId);

    @Query("""
            select m.deviceId from CatalogEquivalenceMember m
            where m.equivalenceGroupId in (
                select other.equivalenceGroupId from CatalogEquivalenceMember other
                where other.deviceId = :deviceId
                  and other.equivalenceGroupId in (
                      select g.id from CatalogEquivalenceGroup g where g.groupId = :groupId
                  )
            )
            """)
    List<UUID> findSiblingDeviceIds(@Param("groupId") UUID groupId, @Param("deviceId") UUID deviceId);
}
