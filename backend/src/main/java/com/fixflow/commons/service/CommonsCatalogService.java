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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reading and writing the shared compatibility catalog.
 *
 * <p>Every method here is deliberately shop-blind. Not one takes a workspace id, and adding one would
 * be the mistake this whole phase exists to undo: whether a screen fits a phone is either true for
 * everyone or true for nobody. Stock levels are the opposite, and they stay where they are.
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

    // --- Reads -------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CatalogBrand> listBrands() {
        return brands.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Page<CatalogDevice> searchDevices(String term, int page, int size) {
        return devices.search(safeTerm(term), pageable(page, size));
    }

    @Transactional(readOnly = true)
    public Page<CatalogComponent> searchComponents(String term, int page, int size) {
        return components.search(safeTerm(term), pageable(page, size));
    }

    /**
     * What fits this phone, with the parts resolved.
     *
     * <p>Two queries and a join in memory rather than a fetch join: the fitment list for one device is
     * small and bounded by how many parts a phone has, while a fetch join here would have to be
     * repeated for the component-side lookup below with the sides swapped.
     */
    @Transactional(readOnly = true)
    public List<FitmentView> fitmentsForDevice(UUID deviceId) {
        CatalogDevice device = devices.findById(deviceId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such device"));

        List<CatalogFitment> edges = fitments.findForDevice(device.getId());
        Map<UUID, CatalogComponent> byId = components
                .findAllById(edges.stream().map(CatalogFitment::getComponentId).toList())
                .stream()
                .collect(Collectors.toMap(CatalogComponent::getId, Function.identity()));

        return edges.stream()
                .map(edge -> new FitmentView(edge, byId.get(edge.getComponentId()), device))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDevice> devicesForComponent(UUID componentId) {
        if (!components.existsById(componentId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such component");
        }
        List<UUID> deviceIds = fitments.findForComponent(componentId).stream()
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
    public void recordLookup(UUID deviceId) {
        devices.findById(deviceId).ifPresent(device -> {
            device.setLookupCount(device.getLookupCount() + 1);
            devices.save(device);
        });
    }

    // --- Writes, called only after ContributionService has decided they are allowed ----------------

    @Transactional
    public CatalogBrand findOrCreateBrand(String name, UUID actorId) {
        String trimmed = required(name, "A brand needs a name");
        return brands.findByName(trimmed).orElseGet(() -> {
            CatalogBrand brand = new CatalogBrand();
            brand.setName(trimmed);
            brand.setCreatedBy(actorId);
            return brands.save(brand);
        });
    }

    @Transactional
    public CatalogDevice addDevice(String brandName,
                                   String name,
                                   String variant,
                                   String modelCode,
                                   Integer releaseYear,
                                   UUID actorId) {
        CatalogBrand brand = findOrCreateBrand(brandName, actorId);
        String trimmedName = required(name, "A device needs a name");
        String trimmedVariant = blankToNull(variant);

        // Returns the existing row rather than failing. Two people adding the same phone on the same
        // day is the normal case in a commons, and a conflict error would teach them to stop trying.
        return devices.findByIdentity(brand.getId(), trimmedName, trimmedVariant)
                .orElseGet(() -> {
                    CatalogDevice device = new CatalogDevice();
                    device.setBrandId(brand.getId());
                    device.setName(trimmedName);
                    device.setVariant(trimmedVariant);
                    device.setModelCode(blankToNull(modelCode));
                    device.setReleaseYear(releaseYear);
                    device.setCreatedBy(actorId);
                    return devices.save(device);
                });
    }

    @Transactional
    public CatalogComponent addComponent(String categoryCode,
                                         String name,
                                         String description,
                                         Map<String, Object> attributes,
                                         UUID actorId) {
        String code = required(categoryCode, "A component needs a category").toUpperCase();
        String trimmedName = required(name, "A component needs a name");

        return components.findByIdentity(code, trimmedName).orElseGet(() -> {
            CatalogComponent component = new CatalogComponent();
            component.setCategoryCode(code);
            component.setName(trimmedName);
            component.setDescription(blankToNull(description));
            component.setAttributes(attributes == null ? Map.of() : attributes);
            component.setCreatedBy(actorId);
            return components.save(component);
        });
    }

    @Transactional
    public CatalogFitment addFitment(UUID componentId,
                                     UUID deviceId,
                                     FitQuality quality,
                                     UUID actorId) {
        if (!components.existsById(componentId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such component");
        }
        if (!devices.existsById(deviceId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such device");
        }

        return fitments.findByComponentIdAndDeviceId(componentId, deviceId)
                .map(existing -> {
                    // Re-adding an edge somebody disputed is itself a confirmation, not a duplicate:
                    // two people now disagree, and the counts should say so rather than one silently
                    // overwriting the other.
                    existing.setConfirmations(existing.getConfirmations() + 1);
                    return fitments.save(existing);
                })
                .orElseGet(() -> {
                    CatalogFitment fitment = new CatalogFitment();
                    fitment.setComponentId(componentId);
                    fitment.setDeviceId(deviceId);
                    fitment.setFitQuality(quality == null ? FitQuality.EXACT : quality);
                    fitment.setContributedBy(actorId);
                    fitment.setCreatedBy(actorId);
                    return fitments.save(fitment);
                });
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
        return fitments.save(fitment);
    }

    @Transactional
    public CatalogFitment disputeFitment(UUID fitmentId) {
        CatalogFitment fitment = requireFitment(fitmentId);
        fitment.setDisputes(fitment.getDisputes() + 1);
        fitment.setDisputed(true);
        // Not deleted. An edge under dispute is information; a missing edge is not, and removing it
        // invites the same wrong claim to be re-added next week by somebody who never saw the argument.
        return fitments.save(fitment);
    }

    @Transactional
    public CatalogFitment markVerified(UUID fitmentId, UUID reviewerId) {
        CatalogFitment fitment = requireFitment(fitmentId);
        fitment.setVerifiedBy(reviewerId);
        fitment.setVerifiedAt(java.time.Instant.now());
        fitment.setDisputed(false);
        return fitments.save(fitment);
    }

    private CatalogFitment requireFitment(UUID fitmentId) {
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
