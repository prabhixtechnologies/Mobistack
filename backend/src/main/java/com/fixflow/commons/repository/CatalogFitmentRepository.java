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

    Optional<CatalogFitment> findByGroupIdAndComponentIdAndDeviceId(UUID groupId, UUID componentId, UUID deviceId);

    long countByGroupId(UUID groupId);

    /**
     * What fits this phone inside one group.
     *
     * <p>Disputed edges come last rather than being hidden. Somebody looking at a part they already
     * own needs to know it is contested; removing the row from the answer just means they order it.
     */
    @Query("""
            select f from CatalogFitment f
            where f.groupId = :groupId and f.deviceId = :deviceId
            order by f.disputed asc, f.confirmations desc
            """)
    List<CatalogFitment> findForDevice(@Param("groupId") UUID groupId, @Param("deviceId") UUID deviceId);

    /** What this part fits inside one group. */
    @Query("""
            select f from CatalogFitment f
            where f.groupId = :groupId and f.componentId = :componentId
            order by f.disputed asc, f.confirmations desc
            """)
    List<CatalogFitment> findForComponent(@Param("groupId") UUID groupId, @Param("componentId") UUID componentId);

    List<CatalogFitment> findByComponentIdIn(Collection<UUID> componentIds);
}
