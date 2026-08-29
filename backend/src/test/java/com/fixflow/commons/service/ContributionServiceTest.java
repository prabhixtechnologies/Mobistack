package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.commons.domain.CatalogContribution;
import com.fixflow.commons.domain.CatalogContribution.Kind;
import com.fixflow.commons.domain.CatalogContribution.Status;
import com.fixflow.commons.domain.CatalogContributor;
import com.fixflow.commons.domain.CatalogEntities.CatalogDevice;
import com.fixflow.commons.domain.CatalogEntities.CatalogFitment;
import com.fixflow.commons.domain.CatalogEntities.FitQuality;
import com.fixflow.commons.repository.CatalogContributionRepository;
import com.fixflow.commons.repository.CatalogContributorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContributionServiceTest {

    private CatalogContributionRepository contributions;
    private CatalogContributorRepository contributors;
    private CommonsCatalogService catalog;
    private ContributionService service;

    private final UUID newcomer = UUID.randomUUID();
    private final UUID trusted = UUID.randomUUID();
    private final UUID reviewer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        contributions = mock(CatalogContributionRepository.class);
        contributors = mock(CatalogContributorRepository.class);
        catalog = mock(CommonsCatalogService.class);
        service = new ContributionService(contributions, contributors, catalog);

        when(contributions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contributors.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contributors.findById(newcomer)).thenReturn(Optional.empty());
        when(contributors.findById(trusted)).thenReturn(Optional.of(trustedContributor(trusted)));
    }

    @Test
    void aNewcomersDeviceQueuesForReview() {
        CatalogContribution result = service.submit(newcomer, Kind.ADD_DEVICE, null,
                Map.of("brand", "Xiaomi", "name", "Redmi Note 10"), null);

        assertThat(result.getStatus()).isEqualTo(Status.PENDING);
        verify(catalog, never()).addDevice(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aTrustedContributorsDeviceAppliesImmediately() {
        CatalogDevice device = new CatalogDevice();
        device.setId(UUID.randomUUID());
        when(catalog.addDevice(eq("Xiaomi"), eq("Redmi Note 10"), any(), any(), any(), eq(trusted)))
                .thenReturn(device);

        CatalogContribution result = service.submit(trusted, Kind.ADD_DEVICE, null,
                Map.of("brand", "Xiaomi", "name", "Redmi Note 10"), null);

        assertThat(result.getStatus()).isEqualTo(Status.APPLIED);
        assertThat(result.getAppliedId()).isEqualTo(device.getId());
    }

    @Test
    void aDisputeQueuesEvenFromATrustedContributor() {
        // A dispute removes information other shops are relying on, so it is the one action where
        // being wrong is worse than being slow.
        CatalogContribution result = service.submit(trusted, Kind.DISPUTE_FITMENT, UUID.randomUUID(),
                Map.of(), "Connector does not seat");

        assertThat(result.getStatus()).isEqualTo(Status.PENDING);
        verify(catalog, never()).disputeFitment(any());
    }

    @Test
    void aConfirmationAppliesEvenFromANewcomer() {
        UUID fitmentId = UUID.randomUUID();
        CatalogFitment fitment = new CatalogFitment();
        fitment.setId(fitmentId);
        when(catalog.confirmFitment(fitmentId)).thenReturn(fitment);

        CatalogContribution result =
                service.submit(newcomer, Kind.CONFIRM_FITMENT, fitmentId, Map.of(), null);

        assertThat(result.getStatus()).isEqualTo(Status.APPLIED);
        verify(catalog).confirmFitment(fitmentId);
    }

    @Test
    void aBannedContributorIsRefusedRatherThanIgnored() {
        UUID banned = UUID.randomUUID();
        CatalogContributor standing = CatalogContributor.forUser(banned);
        standing.setBanned(true);
        when(contributors.findById(banned)).thenReturn(Optional.of(standing));

        assertThatThrownBy(() -> service.submit(banned, Kind.ADD_DEVICE, null,
                Map.of("brand", "Xiaomi", "name", "Redmi"), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(contributions, never()).save(any());
    }

    @Test
    void aMissingPayloadKeyIsAValidationFailure() {
        // Applied at submit time for a trusted contributor, so the message reaches the person who can
        // fix it rather than surfacing at review, hours later.
        assertThatThrownBy(() -> service.submit(trusted, Kind.ADD_DEVICE, null,
                Map.of("brand", "Xiaomi"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("name");
    }

    @Test
    void confirmingWithoutATargetIsRefused() {
        assertThatThrownBy(() -> service.submit(newcomer, Kind.CONFIRM_FITMENT, null, Map.of(), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void acceptingAppliesTheChangeAndCreditsTheAuthor() {
        UUID contributionId = UUID.randomUUID();
        CatalogContribution pending = pendingFitment(contributionId);
        CatalogFitment fitment = new CatalogFitment();
        fitment.setId(UUID.randomUUID());
        when(contributions.findById(contributionId)).thenReturn(Optional.of(pending));
        when(catalog.addFitment(any(), any(), eq(FitQuality.EXACT), eq(newcomer))).thenReturn(fitment);

        CatalogContribution accepted = service.accept(reviewer, contributionId, "Checked");

        assertThat(accepted.getStatus()).isEqualTo(Status.APPLIED);
        assertThat(accepted.getReviewedBy()).isEqualTo(reviewer);
        assertThat(accepted.getAppliedId()).isEqualTo(fitment.getId());
        verify(contributors).save(any(CatalogContributor.class));
    }

    @Test
    void reviewingTwiceIsRefused() {
        // Would double-apply the change and double-count the author's record.
        UUID contributionId = UUID.randomUUID();
        CatalogContribution already = pendingFitment(contributionId);
        already.setStatus(Status.APPLIED);
        when(contributions.findById(contributionId)).thenReturn(Optional.of(already));

        assertThatThrownBy(() -> service.accept(reviewer, contributionId, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void rejectingCountsAgainstTheAuthorWithoutApplyingAnything() {
        UUID contributionId = UUID.randomUUID();
        when(contributions.findById(contributionId)).thenReturn(Optional.of(pendingFitment(contributionId)));

        CatalogContribution rejected = service.reject(reviewer, contributionId, "Wrong phone");

        assertThat(rejected.getStatus()).isEqualTo(Status.REJECTED);
        verify(catalog, never()).addFitment(any(), any(), any(), any());
    }

    @Test
    void banningRemovesTrustSoLiftingTheBanDoesNotRestoreIt() {
        UUID someone = UUID.randomUUID();
        when(contributors.findById(someone)).thenReturn(Optional.of(trustedContributor(someone)));

        CatalogContributor banned = service.setBanned(reviewer, someone, true, "Bulk wrong edits");

        assertThat(banned.isBanned()).isTrue();
        assertThat(banned.isTrusted()).isFalse();
        assertThat(banned.getTrustedAt()).isNull();
    }

    @Test
    void standingOfSomeoneUnknownIsEmptyRatherThanMissing() {
        CatalogContributor standing = service.standingOf(newcomer);

        assertThat(standing.getAcceptedCount()).isZero();
        assertThat(standing.isTrusted()).isFalse();
        verify(contributors, never()).save(any());
    }

    private CatalogContribution pendingFitment(UUID id) {
        CatalogContribution contribution = new CatalogContribution();
        contribution.setId(id);
        contribution.setKind(Kind.ADD_FITMENT);
        contribution.setStatus(Status.PENDING);
        contribution.setSubmittedBy(newcomer);
        contribution.setPayload(Map.of(
                "componentId", UUID.randomUUID().toString(),
                "deviceId", UUID.randomUUID().toString()));
        return contribution;
    }

    private static CatalogContributor trustedContributor(UUID userId) {
        CatalogContributor contributor = CatalogContributor.forUser(userId);
        contributor.setTrusted(true);
        return contributor;
    }
}
