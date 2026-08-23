package com.fixflow.search.dto;

import com.fixflow.catalog.domain.StockStatus;
import com.fixflow.catalog.dto.CompatibilityDtos.DeviceSearchHit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class SearchDtos {

    private SearchDtos() {
    }

    @Schema(name = "PartSearchHit")
    public record PartSearchHit(
            UUID variantId,
            UUID productId,
            String productName,
            String variantName,
            String sku,
            String barcode,
            String categoryName,
            String grade,
            int availableQty,
            StockStatus stockStatus,
            BigDecimal price
    ) {
    }

    @Schema(name = "BrandHit")
    public record BrandHit(UUID id, String name, long deviceCount) {
    }

    @Schema(name = "GlobalSearchResponse",
            description = "One query, every kind of match. Devices rank first because "
                    + "the usual intent is 'what fits this phone'.")
    public record GlobalSearchResponse(
            String query,
            List<DeviceSearchHit> devices,
            List<PartSearchHit> parts,
            List<BrandHit> brands,
            /* Set when the query is an exact SKU or barcode, so a scan jumps straight to the part. */
            PartSearchHit exactMatch,
            int totalResults,
            long tookMillis
    ) {
    }
}
