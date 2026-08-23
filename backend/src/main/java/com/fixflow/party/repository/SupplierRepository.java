package com.fixflow.party.repository;

import com.fixflow.party.domain.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByIdAndShopId(UUID id, UUID shopId);

    List<Supplier> findByShopIdAndActiveTrueOrderByNameAsc(UUID shopId);

    @Query("select s from Supplier s where s.shopId = :shopId and lower(s.name) = lower(:name)")
    Optional<Supplier> findByShopIdAndName(@Param("shopId") UUID shopId, @Param("name") String name);

    @Query("""
            select s from Supplier s
            where s.shopId = :shopId
              and (:query = ''
                   or s.normalizedName like concat('%', :query, '%')
                   or s.phone like concat('%', :rawQuery, '%'))
            """)
    Page<Supplier> search(@Param("shopId") UUID shopId,
                          @Param("query") String normalizedQuery,
                          @Param("rawQuery") String rawQuery,
                          Pageable pageable);

    long countByShopId(UUID shopId);
}
