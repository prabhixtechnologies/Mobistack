package com.fixflow.user.repository;

import com.fixflow.user.domain.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @EntityGraph(attributePaths = {"permissions"})
    @Query("select r from Role r where r.shopId is null and r.code = :code")
    Optional<Role> findSystemRoleByCode(@Param("code") String code);

    /** Roles a given shop may assign: the five built-ins plus its own custom ones. */
    @EntityGraph(attributePaths = {"permissions"})
    @Query("select r from Role r where r.shopId is null or r.shopId = :shopId order by r.seniority")
    List<Role> findAssignableForShop(@Param("shopId") UUID shopId);

    @EntityGraph(attributePaths = {"permissions"})
    @Query("select r from Role r where (r.shopId is null or r.shopId = :shopId) and r.code in :codes")
    List<Role> findAssignableByCodes(@Param("shopId") UUID shopId, @Param("codes") Collection<String> codes);
}
