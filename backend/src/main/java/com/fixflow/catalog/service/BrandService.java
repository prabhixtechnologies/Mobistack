package com.fixflow.catalog.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.Brand;
import com.fixflow.catalog.dto.CatalogDtos.BrandRequest;
import com.fixflow.catalog.dto.CatalogDtos.BrandResponse;
import com.fixflow.catalog.repository.BrandRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final DeviceModelRepository deviceModelRepository;
    private final CatalogMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BrandResponse> list(UUID shopId, boolean activeOnly) {
        List<Brand> brands = activeOnly
                ? brandRepository.findByShopIdAndActiveTrueOrderBySortOrderAscNameAsc(shopId)
                : brandRepository.findByShopIdOrderBySortOrderAscNameAsc(shopId);
        return brands.stream()
                .map(brand -> mapper.toResponse(brand, countDevices(shopId, brand.getId())))
                .toList();
    }

    @Transactional
    public BrandResponse create(UUID shopId, BrandRequest request) {
        brandRepository.findByShopIdAndName(shopId, request.name()).ifPresent(existing -> {
            throw ApiException.alreadyExists("Brand \"" + request.name() + "\" already exists.");
        });

        Brand brand = new Brand();
        brand.setShopId(shopId);
        apply(brand, request);
        brandRepository.save(brand);

        auditService.record(AuditAction.BRAND_CREATED, "Brand", brand.getId(),
                "Added brand \"%s\"".formatted(brand.getName()));
        return mapper.toResponse(brand, 0);
    }

    @Transactional
    public BrandResponse update(UUID shopId, UUID id, BrandRequest request) {
        Brand brand = brandRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Brand", id));
        apply(brand, request);
        brandRepository.save(brand);
        return mapper.toResponse(brand, countDevices(shopId, id));
    }

    /** Finds an existing brand by name or creates it; used by device entry and import. */
    @Transactional
    public Brand findOrCreate(UUID shopId, String name) {
        return brandRepository.findByShopIdAndName(shopId, name).orElseGet(() -> {
            Brand brand = new Brand();
            brand.setShopId(shopId);
            brand.setName(name.trim());
            return brandRepository.save(brand);
        });
    }

    private long countDevices(UUID shopId, UUID brandId) {
        return deviceModelRepository.findByShopIdAndBrandId(shopId, brandId, PageRequest.of(0, 1))
                .getTotalElements();
    }

    private void apply(Brand brand, BrandRequest request) {
        brand.setName(request.name().trim());
        brand.setColor(request.color());
        brand.setLogoUrl(request.logoUrl());
        if (request.sortOrder() != null) {
            brand.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            brand.setActive(request.active());
        }
    }
}
