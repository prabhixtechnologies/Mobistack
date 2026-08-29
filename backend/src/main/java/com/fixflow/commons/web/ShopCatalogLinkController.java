package com.fixflow.commons.web;

import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.commons.repository.CatalogComponentRepository;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The seam between the commons and a shop's own stock.
 *
 * <p>Under {@code /api/v1/inventory} rather than {@code /api/v1/commons}, and deliberately: these
 * endpoints read and write one shop's rows, so they are tenant data and go through the workspace guard
 * and the shop's own permissions like everything else. Only the thing being pointed at is shared.
 *
 * <p>This is where the commercial shape of the product falls out without being decided separately. The
 * catalog is free and needs no shop; knowing what you have in stock that fits is the paid half.
 */
@RestController
@RequestMapping("/api/v1/inventory/catalog-links")
@RequiredArgsConstructor
@Tag(name = "Inventory catalog links")
public class ShopCatalogLinkController {

    private final ProductVariantRepository variants;
    private final CatalogComponentRepository components;

    /**
     * Points one of this shop's variants at a part in the shared catalog.
     *
     * <p>Idempotent, and re-pointable: a shop that linked the wrong component fixes it by linking the
     * right one, with no unlink step in between.
     */
    @PutMapping("/{variantId}")
    @PreAuthorize(Authorize.INVENTORY_WRITE)
    @Transactional
    public LinkView link(@PathVariable UUID variantId, @RequestBody LinkRequest request) {
        if (request == null || request.componentId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Name the catalog component to link.");
        }
        if (!components.existsById(request.componentId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such component in the shared catalog");
        }

        ProductVariant variant = requireOwnVariant(variantId);
        variant.setCatalogComponentId(request.componentId());
        variants.save(variant);
        return LinkView.of(variant);
    }

    @DeleteMapping("/{variantId}")
    @PreAuthorize(Authorize.INVENTORY_WRITE)
    @Transactional
    public void unlink(@PathVariable UUID variantId) {
        ProductVariant variant = requireOwnVariant(variantId);
        variant.setCatalogComponentId(null);
        variants.save(variant);
    }

    /**
     * What this shop has in stock that fits a device, answered from the shared catalog.
     *
     * <p>The whole point of the join. The existing device-compatibility lookup can only find what this
     * shop itself recorded, so a shop that never built its private graph gets an empty answer even
     * while holding the part on a shelf.
     */
    @GetMapping("/devices/{catalogDeviceId}/stock")
    @PreAuthorize(Authorize.INVENTORY_READ)
    @Transactional(readOnly = true)
    public List<StockView> stockFor(@PathVariable UUID catalogDeviceId) {
        return variants.findStockForCatalogDevice(CurrentUser.shopId(), catalogDeviceId).stream()
                .map(StockView::of)
                .toList();
    }

    /**
     * Scoped by shop, not just by id.
     *
     * <p>{@code findById} would let a shop link — and therefore read the existence of — another shop's
     * variant by guessing a UUID. Cheap to get wrong and impossible to notice afterwards.
     */
    private ProductVariant requireOwnVariant(UUID variantId) {
        return variants.findByIdAndShopId(variantId, CurrentUser.shopId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such variant"));
    }

    public record LinkRequest(UUID componentId) {
    }

    public record LinkView(UUID variantId, String sku, UUID componentId) {
        static LinkView of(ProductVariant variant) {
            return new LinkView(variant.getId(), variant.getSku(), variant.getCatalogComponentId());
        }
    }

    public record StockView(UUID variantId,
                            String sku,
                            String name,
                            int available,
                            UUID componentId) {
        static StockView of(ProductVariant variant) {
            return new StockView(variant.getId(), variant.getSku(), variant.getVariantName(),
                    variant.available(), variant.getCatalogComponentId());
        }
    }
}
