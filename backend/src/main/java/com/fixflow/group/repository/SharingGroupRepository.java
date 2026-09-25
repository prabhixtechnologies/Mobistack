package com.fixflow.group.repository;

import com.fixflow.group.domain.SharingGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharingGroupRepository extends JpaRepository<SharingGroup, UUID> {

    Optional<SharingGroup> findFirstByName(String name);

    Optional<SharingGroup> findByJoinCodeIgnoreCase(String joinCode);

    @Query("""
            select distinct g from SharingGroup g
            where g.ownerUserId = :userId
               or exists (
                    select m.id from SharingGroupMember m
                    where m.groupId = g.id and m.userId = :userId
               )
               or (:hasShop = true and exists (
                    select s.id from SharingGroupMember s
                    where s.groupId = g.id and s.workspaceId = :shopId
               ))
            order by g.name asc
            """)
    List<SharingGroup> visibleTo(@Param("userId") UUID userId,
                                 @Param("shopId") UUID shopId,
                                 @Param("hasShop") boolean hasShop);
}
