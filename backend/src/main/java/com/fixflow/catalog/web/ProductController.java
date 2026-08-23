package com.fixflow.catalog.web;

import com.fixflow.catalog.dto.CatalogDtos.CompatibilityLinkRequest;
import com.fixflow.catalog.dto.CatalogDtos.CompatibilityLinkResponse;
import com.fixflow.catalog.dto.CatalogDtos.ProductRequest;
import com.fixflow.catalog.dto.CatalogDtos.ProductResponse;
import com.fixflow.catalog.dto.CatalogDtos.ProductVariantRequest;
import com.fixflow.catalog.dto.CatalogDtos.ProductVariantResponse;
import com.fixflow.catalog.service.ProductService;
import com.fixflow.common.web.PageResponse;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/products")
    @PreAuthorize(Authorize.INVENTORY_READ)
    public PageResponse<ProductResponse> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) UUID categoryId,
                                              @RequestParam(required = false) UUID brandId,
                                              @RequestParam(defaultValue = "true") boolean activeOnly,
                                              @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(productService.list(CurrentUser.shopId(), q, categoryId, brandId,
                activeOnly, pageable));
    }

    @GetMapping("/products/{id}")
    @PreAuthorize(Authorize.INVENTORY_READ)
    public ProductResponse get(@PathVariable UUID id) {
        return productService.get(CurrentUser.shopId(), id);
    }

    @PostMapping("/products")
    @PreAuthorize(Authorize.INVENTORY_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(CurrentUser.shopId(), request);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize(Authorize.INVENTORY_WRITE)
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(CurrentUser.shopId(), id, request);
    }

    @PostMapping("/products/{id}/variants")
    @PreAuthorize(Authorize.INVENTORY_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductVariantResponse addVariant(@PathVariable UUID id,
                                             @Valid @RequestBody ProductVariantRequest request) {
        return productService.addVariant(CurrentUser.shopId(), id, request);
    }

    @PutMapping("/variants/{id}")
    @PreAuthorize(Authorize.INVENTORY_WRITE)
    public ProductVariantResponse updateVariant(@PathVariable UUID id,
                                                @Valid @RequestBody ProductVariantRequest request) {
        return productService.updateVariant(CurrentUser.shopId(), id, request);
    }

    @GetMapping("/variants/{id}")
    @PreAuthorize(Authorize.INVENTORY_READ)
    public ProductVariantResponse getVariant(@PathVariable UUID id) {
        return productService.getVariant(CurrentUser.shopId(), id);
    }

    @GetMapping("/variants")
    @PreAuthorize(Authorize.INVENTORY_READ)
    public PageResponse<ProductVariantResponse> searchVariants(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(productService.searchVariants(CurrentUser.shopId(), q, categoryId,
                brandId, supplierId, lowStockOnly, inStockOnly, pageable));
    }

    @PostMapping("/products/{id}/compatibility")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<CompatibilityLinkResponse> addCompatibility(@PathVariable UUID id,
                                                            @Valid @RequestBody CompatibilityLinkRequest request) {
        return productService.addCompatibility(CurrentUser.shopId(), id, request);
    }

    @DeleteMapping("/products/compatibility/{linkId}")
    @PreAuthorize(Authorize.CATALOG_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCompatibility(@PathVariable UUID linkId) {
        productService.removeCompatibility(CurrentUser.shopId(), linkId);
    }
}
