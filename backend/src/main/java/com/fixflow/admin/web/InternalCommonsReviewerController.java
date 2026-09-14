package com.fixflow.admin.web;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.commons.service.CommonsReviewerService;
import com.fixflow.commons.service.CommonsReviewerService.GrantRequest;
import com.fixflow.commons.service.CommonsReviewerService.ReviewerView;
import com.prabhix.identity.client.ServiceTokenGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * BFF grant of shared-catalog review, over the shared service token.
 *
 * <p>The service token authenticates the product; {@code X-Prabhix-Acting-User} names the
 * staff member. Same contract as Identity's {@code /internal/admin}.
 */
@RestController
@RequestMapping("/internal/admin/commons-reviewers")
@RequiredArgsConstructor
@Tag(name = "Internal commons reviewers")
public class InternalCommonsReviewerController {

    private final ServiceTokenGuard serviceTokens;
    private final CommonsReviewerService reviewers;

    @GetMapping
    public List<ReviewerView> list(HttpServletRequest request) {
        requireActor(request);
        return reviewers.list();
    }

    @PostMapping
    public ReviewerView grant(HttpServletRequest request, @RequestBody GrantRequest body) {
        UUID actor = requireActor(request);
        return reviewers.grant(actor, body == null ? null : body.userId(),
                body == null ? null : body.reason());
    }

    @PostMapping("/{userId}/grant")
    public ReviewerView grantPath(HttpServletRequest request,
                                  @PathVariable UUID userId,
                                  @RequestBody(required = false) GrantRequest body) {
        UUID actor = requireActor(request);
        return reviewers.grant(actor, userId, body == null ? null : body.reason());
    }

    @PostMapping("/{userId}/revoke")
    public void revoke(HttpServletRequest request, @PathVariable UUID userId) {
        requireActor(request);
        reviewers.revoke(userId);
    }

    private UUID requireActor(HttpServletRequest request) {
        if (!serviceTokens.permits(request)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "That service token is not valid");
        }
        return serviceTokens.actingUser(request).orElseThrow(() -> new ApiException(
                ErrorCode.UNAUTHENTICATED, "An admin call must name the acting user"));
    }
}
