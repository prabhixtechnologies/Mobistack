package com.fixflow.party.repository;

import com.fixflow.party.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndShopId(UUID id, UUID shopId);

    Optional<Customer> findByShopIdAndPhone(UUID shopId, String phone);

    @Query("""
            select c from Customer c
            where c.shopId = :shopId
              and (:query = ''
                   or c.normalizedName like concat('%', :query, '%')
                   or c.phone like concat('%', :rawQuery, '%'))
            """)
    Page<Customer> search(@Param("shopId") UUID shopId,
                          @Param("query") String normalizedQuery,
                          @Param("rawQuery") String rawQuery,
                          Pageable pageable);

    long countByShopId(UUID shopId);
}
