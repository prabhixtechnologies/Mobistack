package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CatalogEntities.CatalogFitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogFitmentRepository extends JpaRepository<CatalogFitment, UUID> {

    Optional<CatalogFitment> findByComponentIdAndDeviceId(UUID componentId, UUID deviceId);

    /**
     * What fits this phone.
     *
     * <p>Disputed edges come last rather than being hidden. Somebody looking at a part they already
     * own needs to know it is contested; removing the row from the answer just means they order it.
     */
    @Query("""
            select f from CatalogFitment f
            where f.deviceId = :deviceId
            order by f.disputed asc, f.confirmations desc
            """)
    List<CatalogFitment> findForDevice(@Param("deviceId") UUID deviceId);

    /** What this part fits — the other half of "I have twelve of these, what do they go in". */
    @Query("""
            select f from CatalogFitment f
            where f.componentId = :componentId
            order by f.disputed asc, f.confirmations desc
            """)
    List<CatalogFitment> findForComponent(@Param("componentId") UUID componentId);

    List<CatalogFitment> findByComponentIdIn(Collection<UUID> componentIds);
}
