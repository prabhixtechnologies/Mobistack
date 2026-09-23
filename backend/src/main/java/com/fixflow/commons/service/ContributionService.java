package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.commons.domain.CatalogContribution;
import com.fixflow.commons.domain.CatalogContribution.Kind;
import com.fixflow.commons.domain.CatalogContribution.Status;
import com.fixflow.commons.domain.CatalogContributor;
import com.fixflow.commons.domain.CatalogEntities.CatalogComponent;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.domain.CatalogEntities.CatalogFitment;
import com.fixflow.commons.repository.CatalogContributionRepository;
import com.fixflow.commons.repository.CatalogContributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who may change the commons, and what happens when they try.
 *
 * <p>The rule in one sentence: a trusted contributor's change applies immediately, everybody else's
 * queues for review, and a dispute always queues no matter who filed it. That last exception is the
 * important one — a dispute removes information other people are relying on, so it is the single action
 * where being wrong is worse than being slow.
 *
 * <p>Nothing here takes a workspace. Contributing to the commons requires an identity and a standing,
 * not a shop, which is what makes the free half of the product usable before anyone has signed up for
 * the paid half.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionService {

    private final CatalogContributionRepository contributions;
    private final CatalogContributorRepository contributors;
    private final CommonsCatalogService catalog;

    /**
     * Records a proposal, and applies it if the author has earned that.
     *
     * @return the contribution, either {@code APPLIED} or {@code PENDING}
     */
    @Transactional
    public CatalogContribution submit(UUID authorId,
                                      Kind kind,
                                      UUID targetId,
                                      Map<String, Object> payload,
                                      String reason) {
        return submit(authorId, kind, targetId, payload, reason, null);
    }

    @Transactional
    public CatalogContribution submit(UUID authorId,
                                      Kind kind,
                                      UUID targetId,
                                      Map<String, Object> payload,
                                      String reason,
                                      UUID groupId) {
        CatalogContributor author = standingOf(authorId);
        if (author.isBanned()) {
            // Named as a refusal rather than accepted and dropped. Silently discarding a banned
            // contributor's submissions means they keep sending them and nobody learns anything.
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Your contributions to the shared catalog are suspended.");
        }

        CatalogContribution contribution = new CatalogContribution();
        contribution.setKind(kind);
        contribution.setTargetId(targetId);
        contribution.setPayload(payload == null ? Map.of() : payload);
        contribution.setReason(reason);
        contribution.setSubmittedBy(authorId);
        contribution.setCreatedBy(authorId);
        contribution.setGroupId(groupId);

        if (appliesImmediately(kind, author)) {
            apply(contribution, authorId);
        }
        return contributions.save(contribution);
    }

    /**
     * Whether this person's change lands without waiting.
     *
     * <p>A confirmation is exempt from trust entirely: it only increments a counter, which is the
     * cheapest signal in the system and the one most likely to be offered by somebody who will not
     * fill in a form. A dispute is exempt in the other direction, always queuing.
     */
    private boolean appliesImmediately(Kind kind, CatalogContributor author) {
        if (kind == Kind.DISPUTE_FITMENT) {
            return false;
        }
        if (kind == Kind.CONFIRM_FITMENT) {
            return true;
        }
        return author.isTrusted();
    }

    @Transactional(readOnly = true)
    public Page<CatalogContribution> queue(int page, int size) {
        return contributions.findByStatusOrderByCreatedAtAsc(
                Status.PENDING, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Transactional(readOnly = true)
    public Page<CatalogContribution> mine(UUID authorId, int page, int size) {
        return contributions.findBySubmittedByOrderByCreatedAtDesc(
                authorId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Transactional
    public CatalogContribution accept(UUID reviewerId, UUID contributionId, String note) {
        CatalogContribution contribution = requirePending(contributionId);
        apply(contribution, contribution.getSubmittedBy());
        contribution.setReviewedBy(reviewerId);
        contribution.setReviewedAt(Instant.now());
        contribution.setReviewNote(note);
        bumpAccepted(contribution.getSubmittedBy());
        log.info("Commons contribution {} accepted by {}", contributionId, reviewerId);
        return contributions.save(contribution);
    }

    @Transactional
    public CatalogContribution reject(UUID reviewerId, UUID contributionId, String note) {
        CatalogContribution contribution = requirePending(contributionId);
        contribution.setStatus(Status.REJECTED);
        contribution.setReviewedBy(reviewerId);
        contribution.setReviewedAt(Instant.now());
        contribution.setReviewNote(note);
        bumpRejected(contribution.getSubmittedBy());
        return contributions.save(contribution);
    }

    /**
     * Promotes somebody to trusted, or takes it back.
     *
     * <p>A granted decision rather than a threshold on {@code acceptedCount}. An automatic promotion at
     * N accepted contributions is a thing to game: submit N trivially-correct rows, then the wrong one
     * that matters.
     */
    @Transactional
    public CatalogContributor setTrusted(UUID reviewerId, UUID userId, boolean trusted) {
        CatalogContributor contributor = standingOf(userId);
        contributor.setTrusted(trusted);
        contributor.setTrustedAt(trusted ? Instant.now() : null);
        contributor.setTrustedBy(trusted ? reviewerId : null);
        contributor.setUpdatedAt(Instant.now());
        log.info("Commons contributor {} {} by {}", userId, trusted ? "trusted" : "untrusted", reviewerId);
        return contributors.save(contributor);
    }

    @Transactional
    public CatalogContributor setBanned(UUID reviewerId, UUID userId, boolean banned, String reason) {
        CatalogContributor contributor = standingOf(userId);
        contributor.setBanned(banned);
        contributor.setBannedReason(banned ? reason : null);
        if (banned) {
            // Trust and a ban cannot coexist: a banned contributor whose trust flag survived would
            // write directly again the moment the ban was lifted, with nobody having re-decided that.
            contributor.setTrusted(false);
            contributor.setTrustedAt(null);
            contributor.setTrustedBy(null);
        }
        contributor.setUpdatedAt(Instant.now());
        log.warn("Commons contributor {} {} by {}: {}",
                userId, banned ? "banned" : "unbanned", reviewerId, reason);
        return contributors.save(contributor);
    }

    /**
     * Somebody's standing, invented on the spot if they have never contributed.
     *
     * <p>Deliberately does not save. Every caller either only reads it — the ban check on submit, the
     * standing endpoint — or mutates and saves it itself, and persisting here as well meant two writes
     * for one accepted contribution. A contributor row therefore appears on the first contribution
     * rather than the first read.
     */
    @Transactional(readOnly = true)
    public CatalogContributor standingOf(UUID userId) {
        return contributors.findById(userId).orElseGet(() -> CatalogContributor.forUser(userId));
    }

    /**
     * Turns a proposal into rows.
     *
     * <p>Payload keys are read defensively because the payload is JSON a client wrote: a missing key is
     * a validation failure with a message naming what is missing, not a NullPointerException at review
     * time, hours after the person who could fix it has gone.
     */
    private void apply(CatalogContribution contribution, UUID authorId) {
        UUID produced = switch (contribution.getKind()) {
            case ADD_DEVICE -> {
                CatalogDevice device = catalog.addDevice(
                        text(contribution, "brand"),
                        text(contribution, "name"),
                        optional(contribution, "variant"),
                        optional(contribution, "modelCode"),
                        integer(contribution, "releaseYear"),
                        authorId);
                yield device.getId();
            }
            case ADD_COMPONENT -> {
                CatalogComponent component = catalog.addComponent(
                        text(contribution, "categoryCode"),
                        text(contribution, "name"),
                        optional(contribution, "description"),
                        attributes(contribution),
                        authorId);
                attachFits(contribution, component.getId(), authorId);
                yield component.getId();
            }
            case ADD_FITMENT -> {
                CatalogFitment fitment = catalog.addFitment(
                        uuid(contribution, "componentId"),
                        uuid(contribution, "deviceId"),
                        CommonsCatalogService.parseFit(optional(contribution, "fit")),
                        authorId,
                        contribution.getGroupId());
                yield fitment.getId();
            }
            case CONFIRM_FITMENT -> {
                assertSameGroup(contribution);
                yield catalog.confirmFitment(requireTarget(contribution)).getId();
            }
            case DISPUTE_FITMENT -> {
                assertSameGroup(contribution);
                yield catalog.disputeFitment(requireTarget(contribution)).getId();
            }
        };

        contribution.setStatus(Status.APPLIED);
        contribution.setAppliedId(produced);
    }

    private static UUID requireTarget(CatalogContribution contribution) {
        UUID target = contribution.getTargetId();
        if (target == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Confirming or disputing needs the fitment it refers to.");
        }
        return target;
    }

    private CatalogContribution requirePending(UUID contributionId) {
        CatalogContribution contribution = contributions.findById(contributionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such contribution"));
        if (contribution.getStatus() != Status.PENDING) {
            // Reviewing twice would double-apply the change and double-count the author's record.
            throw new ApiException(ErrorCode.CONFLICT, "That contribution has already been reviewed.");
        }
        return contribution;
    }

    private void bumpAccepted(UUID userId) {
        CatalogContributor contributor = standingOf(userId);
        contributor.setAcceptedCount(contributor.getAcceptedCount() + 1);
        contributor.setUpdatedAt(Instant.now());
        contributors.save(contributor);
    }

    private void bumpRejected(UUID userId) {
        CatalogContributor contributor = standingOf(userId);
        contributor.setRejectedCount(contributor.getRejectedCount() + 1);
        contributor.setUpdatedAt(Instant.now());
        contributors.save(contributor);
    }

    private static String text(CatalogContribution contribution, String key) {
        Object value = contribution.getPayload().get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This contribution needs a " + key + ".");
        }
        return value.toString().trim();
    }

    private static String optional(CatalogContribution contribution, String key) {
        Object value = contribution.getPayload().get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static Integer integer(CatalogContribution contribution, String key) {
        Object value = contribution.getPayload().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, key + " must be a number.");
        }
    }

    private static UUID uuid(CatalogContribution contribution, String key) {
        String raw = text(contribution, key);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, key + " must be an id.");
        }
    }

    /**
     * Phones named on a new part. One contribution so review accepts the part and its links together.
     */
    private void assertSameGroup(CatalogContribution contribution) {
        if (contribution.getGroupId() == null || contribution.getTargetId() == null) {
            return;
        }
        CatalogFitment fitment = catalog.requireFitment(contribution.getTargetId());
        if (fitment.getGroupId() != null && !fitment.getGroupId().equals(contribution.getGroupId())) {
            throw ApiException.forbidden("That fitment belongs to another group.");
        }
    }

    private void attachFits(CatalogContribution contribution, UUID componentId, UUID authorId) {
        Object raw = contribution.getPayload().get("fits");
        if (raw == null) {
            return;
        }
        if (!(raw instanceof List<?> fits)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "fits must be a list of phones.");
        }
        for (Object item : fits) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Each fit needs a brand and a name.");
            }
            String brand = requiredText(map.get("brand"), "brand");
            String name = requiredText(map.get("name"), "name");
            Object fit = map.get("fit");
            CatalogDevice device = catalog.findDeviceByName(brand, name)
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                            "No phone named " + brand + " " + name + " in the catalog."));
            catalog.addFitment(componentId, device.getId(),
                    CommonsCatalogService.parseFit(fit == null ? null : fit.toString()), authorId,
                    contribution.getGroupId());
        }
    }

    private static String requiredText(Object value, String key) {
        if (value == null || value.toString().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Each fit needs a " + key + ".");
        }
        return value.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attributes(CatalogContribution contribution) {
        Object value = contribution.getPayload().get("attributes");
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }
}
