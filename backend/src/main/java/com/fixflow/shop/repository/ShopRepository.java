package com.fixflow.shop.repository;

import com.fixflow.shop.domain.Shop;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShopRepository extends JpaRepository<Shop, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByJoinCode(String joinCode);

    Optional<Shop> findByJoinCodeIgnoreCase(String joinCode);

    /**
     * Pessimistic lock so two concurrent invoices can never claim the same number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shop s where s.id = :id")
    Optional<Shop> findByIdForUpdate(@Param("id") UUID id);
}
