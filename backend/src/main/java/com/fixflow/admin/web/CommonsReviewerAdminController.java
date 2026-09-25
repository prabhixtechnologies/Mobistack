package com.fixflow.admin.web;

import com.fixflow.admin.service.PlatformAdminService;
import com.fixflow.commons.service.CommonsReviewerService;
import com.fixflow.commons.service.CommonsReviewerService.GrantRequest;
import com.fixflow.commons.service.CommonsReviewerService.ReviewerView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff grant of shared-catalog review.
 *
 * <p>The oneOps BFF is the intended writer, over {@code /internal/admin/commons-reviewers}.
 * These routes exist so a staff member can grant from the MobiStack admin console when
 * that BFF is not ready yet.
 */
@RestController
@RequestMapping("/api/v1/mobistack/admin/commons-reviewers")
@RequiredArgsConstructor
@Tag(name = "Commons reviewers")
public class CommonsReviewerAdminController {

    private final PlatformAdminService platformAdminService;
    private final CommonsReviewerService reviewers;

    @GetMapping
    public List<ReviewerView> list() {
        platformAdminService.requireAdmin();
        return reviewers.list();
    }

    @PostMapping
    public ReviewerView grant(@RequestBody GrantRequest request) {
        UUID actor = platformAdminService.requireAdmin();
        UUID userId = request == null ? null : request.userId();
        String reason = request == null ? null : request.reason();
        return reviewers.grant(actor, userId, reason);
    }

    @PostMapping("/grant")
    public ReviewerView grantPath(@RequestParam UUID userId, @RequestBody(required = false) GrantRequest request) {
        UUID actor = platformAdminService.requireAdmin();
        return reviewers.grant(actor, userId, request == null ? null : request.reason());
    }

    @PostMapping("/revoke")
    public void revoke(@RequestParam UUID userId) {
        platformAdminService.requireAdmin();
        reviewers.revoke(userId);
    }
}
