package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.commons.domain.CatalogEntities.CatalogBrand;
import com.fixflow.commons.domain.CatalogEntities.CatalogComponent;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.domain.CatalogEntities.CatalogDeviceAlias;
import com.fixflow.commons.domain.CatalogEntities.CatalogFitment;
import com.fixflow.commons.domain.CatalogEntities.FitQuality;
import com.fixflow.commons.repository.CatalogBrandRepository;
import com.fixflow.commons.repository.CatalogComponentRepository;
import com.fixflow.commons.repository.CatalogDeviceAliasRepository;
import com.fixflow.commons.repository.CatalogDeviceRepository;
import com.fixflow.commons.repository.CatalogFitmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reading and writing the shared compatibility catalog.
 *
 * <p>Phone and part names are a shared reference. Fitment rows belong to one sharing group: the same
 * part can fit a phone for one group and not for another. Stock levels stay on the shop.
 *
 * <p>Writes arrive through {@link ContributionService} rather than here, so that the rules about who
 * may change the commons live in one place. This class is what does the changing once that has been
 * decided.
 */
@Service
@RequiredArgsConstructor
public class CommonsCatalogService {

    /** Enough to stop a client asking for the whole graph in one request. */
    private static final int MAX_PAGE_SIZE = 100;

    private final CatalogBrandRepository brands;
    private final CatalogDeviceRepository devices;
    private final CatalogDeviceAliasRepository aliases;
    private final CatalogComponentRepository components;
    private final CatalogFitmentRepository fitments;
    private final CatalogCache cache;

    // --- Reads -------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CatalogBrand> listBrands(UUID groupId) {
        requireGroup(groupId);
        return brands.findByGroupIdOrderByNameAsc(groupId);
    }

    @Transactional(readOnly = true)
    public Page<CatalogDevice> searchDevices(UUID groupId, String term, int page, int size) {
        requireGroup(groupId);
        return devices.search(groupId, safeTerm(term), pageable(page, size));
    }

    /** Browse without a query: most-looked-up models first. */
    @Transactional(readOnly = true)
    public Page<CatalogDevice> listDevices(UUID groupId, int page, int size) {
        requireGroup(groupId);
        return devices.findByGroupId(groupId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "lookupCount").and(Sort.by("name"))));
    }

    @Transactional(readOnly = true)
    public Page<CatalogComponent> listComponents(UUID groupId, int page, int size) {
        requireGroup(groupId);
        return components.findByGroupId(groupId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("name")));
    }

    @Transactional(readOnly = true)
    public Page<CatalogDevice> devicesForBrand(UUID groupId, UUID brandId, int page, int size) {
        CatalogBrand brand = requireBrand(groupId, brandId);
        return devices.findByGroupIdAndBrandIdOrderByNameAsc(brand.getGroupId(), brand.getId(), pageable(page, size));
    }

    @Transactional(readOnly = true)
    public CatalogDevice requireDevice(UUID groupId, UUID deviceId) {
        requireGroup(groupId);
        return devices.findByIdAndGroupId(deviceId, groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such device"));
    }

    @Transactional(readOnly = true)
    public CatalogComponent requireComponent(UUID groupId, UUID componentId) {
        requireGroup(groupId);
        return components.findByIdAndGroupId(componentId, groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such component"));
    }

    @Transactional(readOnly = true)
    public List<CatalogDevice> popularDevices(UUID groupId, int limit) {
        requireGroup(groupId);
        return devices.findByGroupId(groupId, PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_PAGE_SIZE),
                Sort.by("lookupCount").descending().and(Sort.by("name")))).getContent();
    }

    @Transactional(readOnly = true)
    public long brandCount(UUID groupId) {
        return groupId == null ? 0 : brands.countByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public long deviceCount(UUID groupId) {
        return groupId == null ? 0 : devices.countByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public long componentCount(UUID groupId) {
        return groupId == null ? 0 : components.countByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public long fitmentCount() {
        return fitments.count();
    }

    @Transactional(readOnly = true)
    public long fitmentCount(UUID groupId) {
        if (groupId == null) {
            return 0;
        }
        return fitments.countByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> brandNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return brands.findAllById(ids).stream()
                .collect(Collectors.toMap(CatalogBrand::getId, CatalogBrand::getName));
    }

    @Transactional(readOnly = true)
    public Page<CatalogComponent> searchComponents(UUID groupId, String term, int page, int size) {
        requireGroup(groupId);
        return components.search(groupId, safeTerm(term), pageable(page, size));
    }

    /**
     * What fits this phone, with the parts resolved.
     *
     * <p>Two queries and a join in memory rather than a fetch join: the fitment list for one device is
     * small and bounded by how many parts a phone has, while a fetch join here would have to be
     * repeated for the component-side lookup below with the sides swapped.
     */
    @Transactional(readOnly = true)
    public List<FitmentView> fitmentsForDevice(UUID deviceId, UUID groupId) {
        if (groupId == null) {
            return List.of();
        }
        CatalogDevice device = requireDevice(groupId, deviceId);

        List<CatalogFitment> edges = fitments.findForDevice(groupId, device.getId());
        Map<UUID, CatalogComponent> byId = components
                .findAllById(edges.stream().map(CatalogFitment::getComponentId).toList())
                .stream()
                .collect(Collectors.toMap(CatalogComponent::getId, Function.identity()));

        return edges.stream()
                .map(edge -> new FitmentView(edge, byId.get(edge.getComponentId()), device))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDevice> devicesForComponent(UUID componentId, UUID groupId) {
        if (groupId == null) {
            return List.of();
        }
        requireComponent(groupId, componentId);
        List<UUID> deviceIds = fitments.findForComponent(groupId, componentId).stream()
                .map(CatalogFitment::getDeviceId)
                .toList();
        return devices.findAllById(deviceIds);
    }

    @Transactional(readOnly = true)
    public List<CatalogDeviceAlias> aliasesFor(UUID deviceId) {
        return aliases.findByDeviceId(deviceId);
    }

    @Transactional(readOnly = true)
    public Optional<CatalogComponent> findComponent(UUID componentId) {
        return components.findById(componentId);
    }

    /**
     * Records that somebody looked this device up.
     *
     * <p>Drives search ordering, which is the only reason it exists. Deliberately not part of the read
     * transaction: a failed counter update must not fail the lookup that triggered it, and two
     * concurrent lookups incrementing the same row is not worth a lock.
     */
    @Transactional
    public void recordLookup(UUID groupId, UUID deviceId) {
        devices.findByIdAndGroupId(deviceId, groupId).ifPresent(device -> {
            device.setLookupCount(device.getLookupCount() + 1);
            devices.save(device);
        });
    }

    // --- Writes, called only after ContributionService has decided they are allowed ----------------

    @Transactional
    public CatalogBrand findOrCreateBrand(String name, UUID actorId, UUID groupId) {
        requireGroup(groupId);
        String trimmed = required(name, "A brand needs a name");
        CatalogBrand brand = brands.findByName(groupId, trimmed).orElseGet(() -> {
            CatalogBrand created = new CatalogBrand();
            created.setGroupId(groupId);
            created.setName(trimmed);
            created.setCreatedBy(actorId);
            return brands.save(created);
        });
        cache.evictGroupAfterCommit(groupId);
        return brand;
    }

    @Transactional
    public CatalogDevice addDevice(String brandName,
                                   String name,
                                   String variant,
                                   String modelCode,
                                   Integer releaseYear,
                                   UUID actorId,
                                   UUID groupId) {
        CatalogBrand brand = findOrCreateBrand(brandName, actorId, groupId);
        String trimmedName = required(name, "A device needs a name");
        String trimmedVariant = blankToNull(variant);

        // Returns the existing row rather than failing. Two people adding the same phone on the same
        // day is the normal case in a commons, and a conflict error would teach them to stop trying.
        CatalogDevice device = devices.findByIdentity(groupId, brand.getId(), trimmedName, trimmedVariant)
                .orElseGet(() -> {
                    CatalogDevice created = new CatalogDevice();
                    created.setGroupId(groupId);
                    created.setBrandId(brand.getId());
                    created.setName(trimmedName);
                    created.setVariant(trimmedVariant);
                    created.setModelCode(blankToNull(modelCode));
                    created.setReleaseYear(releaseYear);
                    created.setCreatedBy(actorId);
                    return devices.save(created);
                });
        cache.evictGroupAfterCommit(groupId);
        return device;
    }

    /**
     * The phone a fitment names, matched the same way a duplicate device is refused.
     */
    @Transactional(readOnly = true)
    public Optional<CatalogDevice> findDeviceByName(UUID groupId, String brandName, String name) {
        if (groupId == null || brandName == null || brandName.isBlank() || name == null || name.isBlank()) {
            return Optional.empty();
        }
        return brands.findByName(groupId, brandName.trim())
                .flatMap(brand -> devices.findByIdentity(groupId, brand.getId(), name.trim(), null));
    }

    @Transactional
    public CatalogComponent addComponent(String categoryCode,
                                         String name,
                                         String description,
                                         Map<String, Object> attributes,
                                         UUID actorId,
                                         UUID groupId) {
        requireGroup(groupId);
        String code = required(categoryCode, "A component needs a category").toUpperCase();
        String trimmedName = required(name, "A component needs a name");

        CatalogComponent component = components.findByIdentity(groupId, code, trimmedName).orElseGet(() -> {
            CatalogComponent created = new CatalogComponent();
            created.setGroupId(groupId);
            created.setCategoryCode(code);
            created.setName(trimmedName);
            created.setDescription(blankToNull(description));
            created.setAttributes(attributes == null ? Map.of() : attributes);
            created.setCreatedBy(actorId);
            return components.save(created);
        });
        cache.evictGroupAfterCommit(groupId);
        return component;
    }

    @Transactional
    public CatalogFitment addFitment(UUID componentId,
                                     UUID deviceId,
                                     FitQuality quality,
                                     UUID actorId,
                                     UUID groupId) {
        if (groupId == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a fitment group.");
        }
        requireComponent(groupId, componentId);
        requireDevice(groupId, deviceId);

        CatalogFitment saved = fitments.findByGroupIdAndComponentIdAndDeviceId(groupId, componentId, deviceId)
                .map(existing -> {
                    existing.setConfirmations(existing.getConfirmations() + 1);
                    return fitments.save(existing);
                })
                .orElseGet(() -> {
                    CatalogFitment fitment = new CatalogFitment();
                    fitment.setGroupId(groupId);
                    fitment.setComponentId(componentId);
                    fitment.setDeviceId(deviceId);
                    fitment.setFitQuality(quality == null ? FitQuality.EXACT : quality);
                    fitment.setContributedBy(actorId);
                    fitment.setCreatedBy(actorId);
                    return fitments.save(fitment);
                });
        cache.evictGroupAfterCommit(groupId);
        return saved;
    }

    @Transactional
    public CatalogFitment confirmFitment(UUID fitmentId) {
        CatalogFitment fitment = requireFitment(fitmentId);
        fitment.setConfirmations(fitment.getConfirmations() + 1);
        // Enough people have now said it works that the earlier objection is outweighed. The dispute
        // count stays, so the history of the disagreement is not erased.
        if (fitment.isDisputed() && fitment.getConfirmations() > fitment.getDisputes() * 2) {
            fitment.setDisputed(false);
        }
        CatalogFitment saved = fitments.save(fitment);
        cache.evictGroupAfterCommit(fitment.getGroupId());
        return saved;
    }

    @Transactional
    public CatalogFitment disputeFitment(UUID fitmentId) {
        CatalogFitment fitment = requireFitment(fitmentId);
        fitment.setDisputes(fitment.getDisputes() + 1);
        fitment.setDisputed(true);
        CatalogFitment saved = fitments.save(fitment);
        cache.evictGroupAfterCommit(fitment.getGroupId());
        return saved;
    }

    @Transactional
    public CatalogFitment markVerified(UUID fitmentId, UUID reviewerId) {
        CatalogFitment fitment = requireFitment(fitmentId);
        fitment.setVerifiedBy(reviewerId);
        fitment.setVerifiedAt(java.time.Instant.now());
        fitment.setDisputed(false);
        CatalogFitment saved = fitments.save(fitment);
        cache.evictGroupAfterCommit(fitment.getGroupId());
        return saved;
    }

    public CatalogFitment requireFitment(UUID fitmentId) {
        return fitments.findById(fitmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such fitment"));
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    /**
     * Refuses an empty search rather than returning the entire catalog.
     *
     * <p>A blank term in a {@code like '%%'} matches every row, which is a table scan of the largest
     * table in the system dressed up as a search.
     */
    private static String safeTerm(String term) {
        if (term == null || term.trim().length() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Type at least two characters to search.");
        }
        return term.trim();
    }

    private CatalogBrand requireBrand(UUID groupId, UUID brandId) {
        requireGroup(groupId);
        CatalogBrand brand = brands.findById(brandId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such brand"));
        if (!groupId.equals(brand.getGroupId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such brand");
        }
        return brand;
    }

    public void requireGroup(UUID groupId) {
        if (groupId == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a fitment group.");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Parses what a client sent, refusing anything else.
     *
     * <p>Not left to Jackson's enum coercion, which answers a bad value with a 400 whose message names
     * the Java type. A caller who typed "PERFECT" should be told the three words that work.
     */
    public static FitQuality parseFit(String raw) {
        if (raw == null || raw.isBlank()) {
            return FitQuality.EXACT;
        }
        try {
            return FitQuality.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Fit must be EXACT, COMPATIBLE or REQUIRES_MODIFICATION.");
        }
    }

    /** One edge, with both ends resolved, so a caller does not have to fetch them separately. */
    public record FitmentView(CatalogFitment fitment,
                              CatalogComponent component,
                              CatalogDevice device) {
    }
}
