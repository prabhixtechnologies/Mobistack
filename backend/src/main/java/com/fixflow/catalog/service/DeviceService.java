package com.fixflow.catalog.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.DeviceNameParser;
import com.fixflow.catalog.domain.Brand;
import com.fixflow.catalog.domain.DeviceAlias;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.dto.CatalogDtos.AddAliasRequest;
import com.fixflow.catalog.dto.CatalogDtos.DeviceModelRequest;
import com.fixflow.catalog.dto.CatalogDtos.DeviceModelResponse;
import com.fixflow.catalog.dto.CatalogDtos.ResolveDeviceRequest;
import com.fixflow.catalog.repository.BrandRepository;
import com.fixflow.catalog.repository.CompatibilityGroupDeviceRepository;
import com.fixflow.catalog.repository.DeviceAliasRepository;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceModelRepository deviceModelRepository;
    private final DeviceAliasRepository deviceAliasRepository;
    private final CompatibilityGroupDeviceRepository groupDeviceRepository;
    private final BrandRepository brandRepository;
    private final BrandService brandService;
    private final CatalogMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<DeviceModelResponse> list(UUID shopId, UUID brandId, Pageable pageable) {
        Page<DeviceModel> page = brandId == null
                ? deviceModelRepository.findByShopId(shopId, pageable)
                : deviceModelRepository.findByShopIdAndBrandId(shopId, brandId, pageable);
        return page.map(device -> mapper.toResponse(device,
                deviceAliasRepository.findByDeviceModelIdOrderByAliasAsc(device.getId()),
                groupDeviceRepository.findByDeviceModelId(device.getId()).size()));
    }

    @Transactional(readOnly = true)
    public DeviceModelResponse get(UUID shopId, UUID id) {
        DeviceModel device = require(shopId, id);
        return mapper.toResponse(device,
                deviceAliasRepository.findByDeviceModelIdOrderByAliasAsc(id),
                groupDeviceRepository.findByDeviceModelId(id).size());
    }

    @Transactional
    public DeviceModelResponse create(UUID shopId, DeviceModelRequest request) {
        Brand brand = brandRepository.findByIdAndShopId(request.brandId(), shopId)
                .orElseThrow(() -> ApiException.notFound("Brand", request.brandId()));

        String variant = blankToNull(request.variant());
        deviceModelRepository.findByShopIdBrandNameAndVariant(shopId, brand.getId(), request.name().trim(), variant)
                .ifPresent(existing -> {
                    throw ApiException.alreadyExists(
                            "%s already exists.".formatted(com.fixflow.catalog.DeviceLabels.display(
                                    brand.getName(), request.name(), variant)));
                });

        DeviceModel device = new DeviceModel();
        device.setShopId(shopId);
        device.setBrand(brand);
        device.setName(request.name().trim());
        device.setVariant(variant);
        device.setModelCode(request.modelCode());
        device.setReleaseYear(request.releaseYear());
        if (request.active() != null) {
            device.setActive(request.active());
        }
        deviceModelRepository.save(device);

        if (request.aliases() != null) {
            request.aliases().stream()
                    .filter(alias -> alias != null && !alias.isBlank())
                    .forEach(alias -> saveAlias(shopId, device.getId(), alias, DeviceAlias.Source.MANUAL));
        }

        auditService.record(AuditAction.DEVICE_CREATED, "DeviceModel", device.getId(),
                "Added device \"%s %s\"".formatted(brand.getName(), device.getName()));

        return get(shopId, device.getId());
    }

    @Transactional
    public DeviceModelResponse update(UUID shopId, UUID id, DeviceModelRequest request) {
        DeviceModel device = require(shopId, id);
        if (request.brandId() != null && !request.brandId().equals(device.getBrand().getId())) {
            Brand brand = brandRepository.findByIdAndShopId(request.brandId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("Brand", request.brandId()));
            device.setBrand(brand);
        }
        device.setName(request.name().trim());
        device.setVariant(blankToNull(request.variant()));
        device.setModelCode(request.modelCode());
        device.setReleaseYear(request.releaseYear());
        if (request.active() != null) {
            device.setActive(request.active());
        }
        deviceModelRepository.save(device);

        auditService.record(AuditAction.DEVICE_UPDATED, "DeviceModel", id,
                "Updated device \"%s\"".formatted(device.getName()));
        return get(shopId, id);
    }

    @Transactional
    public DeviceModelResponse addAlias(UUID shopId, UUID deviceId, AddAliasRequest request) {
        DeviceModel device = require(shopId, deviceId);

        // An alias must be unambiguous shop-wide: two models cannot both answer
        // to "Realme 6i" or search would have to guess.
        deviceAliasRepository.findByShopIdAndAlias(shopId, request.alias()).ifPresent(existing -> {
            if (!existing.getDeviceModelId().equals(deviceId)) {
                throw ApiException.conflict(
                        "\"%s\" is already an alias of another device.".formatted(request.alias()));
            }
            throw ApiException.alreadyExists("\"%s\" is already an alias of this device."
                    .formatted(request.alias()));
        });

        saveAlias(shopId, deviceId, request.alias(), DeviceAlias.Source.MANUAL);
        auditService.record(AuditAction.DEVICE_ALIAS_ADDED, "DeviceModel", deviceId,
                "Added alias \"%s\" to %s".formatted(request.alias(), device.getName()));
        return get(shopId, deviceId);
    }

    @Transactional
    public void removeAlias(UUID shopId, UUID aliasId) {
        DeviceAlias alias = deviceAliasRepository.findByIdAndShopId(aliasId, shopId)
                .orElseThrow(() -> ApiException.notFound("Alias", aliasId));
        deviceAliasRepository.delete(alias);
        auditService.record(AuditAction.DEVICE_ALIAS_REMOVED, "DeviceModel", alias.getDeviceModelId(),
                "Removed alias \"%s\"".formatted(alias.getAlias()));
    }

    /** Used by device creation and by the universal-list importer. */
    @Transactional
    public DeviceAlias saveAlias(UUID shopId, UUID deviceModelId, String aliasText, DeviceAlias.Source source) {
        DeviceAlias alias = new DeviceAlias();
        alias.setShopId(shopId);
        alias.setDeviceModelId(deviceModelId);
        alias.setAlias(aliasText.trim());
        alias.setSource(source);
        return deviceAliasRepository.save(alias);
    }

    @Transactional
    public DeviceModel findOrCreate(UUID shopId, Brand brand, String name) {
        return findOrCreate(shopId, brand, name, null);
    }

    @Transactional
    public DeviceModel findOrCreate(UUID shopId, Brand brand, String name, String variant) {
        String trimmedName = name.trim();
        String normalizedVariant = blankToNull(variant);
        return deviceModelRepository
                .findByShopIdBrandNameAndVariant(shopId, brand.getId(), trimmedName, normalizedVariant)
                .orElseGet(() -> {
                    DeviceModel device = new DeviceModel();
                    device.setShopId(shopId);
                    device.setBrand(brand);
                    device.setName(trimmedName);
                    device.setVariant(normalizedVariant);
                    return deviceModelRepository.save(device);
                });
    }

    /**
     * Parse a counter string into brand + model + variant, creating rows as needed.
     */
    @Transactional
    public DeviceModel findOrCreateFromText(UUID shopId, String text, String defaultBrand) {
        if (text == null || text.isBlank()) {
            throw ApiException.businessRule("Device name is empty.");
        }
        List<String> shopBrands = brandRepository.findByShopIdOrderBySortOrderAscNameAsc(shopId).stream()
                .map(Brand::getName)
                .toList();
        DeviceNameParser.ParsedDeviceName parsed = DeviceNameParser.parse(text, shopBrands);
        String brandName = parsed.brand() != null
                ? parsed.brand()
                : (defaultBrand == null || defaultBrand.isBlank() ? "Generic" : defaultBrand.trim());
        String modelName = parsed.name() == null || parsed.name().isBlank() ? text.trim() : parsed.name();
        Brand brand = brandService.findOrCreate(shopId, brandName);
        return findOrCreate(shopId, brand, modelName, parsed.variant());
    }

    @Transactional
    public DeviceModelResponse resolve(UUID shopId, ResolveDeviceRequest request) {
        DeviceModel device = findOrCreateFromText(shopId, request.text(), request.defaultBrand());
        return get(shopId, device.getId());
    }

    @Transactional(readOnly = true)
    public List<String> aliasesOf(UUID deviceModelId) {
        return deviceAliasRepository.findByDeviceModelIdOrderByAliasAsc(deviceModelId).stream()
                .map(DeviceAlias::getAlias)
                .toList();
    }

    private DeviceModel require(UUID shopId, UUID id) {
        return deviceModelRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Device model", id));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
