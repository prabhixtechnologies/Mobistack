package com.fixflow.user.repository;

import com.fixflow.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findWithRolesByEmail(@Param("email") String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithRolesById(@Param("id") UUID id);

    @Query("select (count(u) > 0) from User u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    @EntityGraph(attributePaths = {"roles"})
    Page<User> findByShopId(UUID shopId, Pageable pageable);

    @EntityGraph(attributePaths = {"roles"})
    List<User> findByShopIdAndActiveTrue(UUID shopId);

    Optional<User> findByIdAndShopId(UUID id, UUID shopId);

    Optional<User> findFirstByPhone(String phone);

    long countByShopIdAndActiveTrue(UUID shopId);

    List<User> findBySystemAdminTrueAndActiveTrue();
}
