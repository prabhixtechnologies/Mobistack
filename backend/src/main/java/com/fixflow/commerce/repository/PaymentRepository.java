package com.fixflow.commerce.repository;

import com.fixflow.commerce.domain.Payment;
import com.fixflow.commerce.domain.PaymentReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByShopIdAndReferenceTypeAndReferenceIdOrderByOccurredAtAsc(
            UUID shopId, PaymentReferenceType type, UUID referenceId);

    /** Payments for a whole page of documents, so listing does not query per row. */
    List<Payment> findByShopIdAndReferenceTypeAndReferenceIdInOrderByOccurredAtAsc(
            UUID shopId, PaymentReferenceType type, Collection<UUID> referenceIds);
}
