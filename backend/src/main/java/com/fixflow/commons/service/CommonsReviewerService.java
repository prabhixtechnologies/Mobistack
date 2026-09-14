package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.commons.domain.CommonsReviewer;
import com.fixflow.commons.repository.CommonsReviewerRepository;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who may review the shared catalog.
 *
 * <p>Granted by Prabhix, never by a shop owner. The table is the source of truth;
 * {@code COMMONS_REVIEW} on the principal is derived from it at authentication.
 */
@Service
@RequiredArgsConstructor
public class CommonsReviewerService {

    public record ReviewerView(UUID userId,
                               String email,
                               String fullName,
                               UUID grantedBy,
                               Instant grantedAt,
                               String reason) {
    }

    public record GrantRequest(UUID userId, String reason) {
    }

    private final CommonsReviewerRepository reviewers;
    private final UserRepository users;

    public boolean isReviewer(UUID userId) {
        return userId != null && reviewers.existsById(userId);
    }

    @Transactional(readOnly = true)
    public List<ReviewerView> list() {
        List<CommonsReviewer> rows = reviewers.findAllByOrderByGrantedAtDesc();
        Map<UUID, User> byId = users.findAllById(rows.stream()
                        .map(CommonsReviewer::getUserId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream()
                .map(row -> toView(row, byId.get(row.getUserId())))
                .toList();
    }

    @Transactional
    public ReviewerView grant(UUID actorId, UUID userId, String reason) {
        if (userId == null) {
            throw new ApiException(com.fixflow.common.error.ErrorCode.VALIDATION_FAILED,
                    "Name the user to grant review to.");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
        CommonsReviewer row = reviewers.findById(userId).orElseGet(CommonsReviewer::new);
        row.setUserId(user.getId());
        row.setGrantedBy(actorId);
        row.setGrantedAt(Instant.now());
        row.setReason(blankToNull(reason));
        reviewers.save(row);
        return toView(row, user);
    }

    @Transactional
    public void revoke(UUID userId) {
        if (userId != null) {
            reviewers.deleteById(userId);
        }
    }

    private static ReviewerView toView(CommonsReviewer row, User user) {
        return new ReviewerView(
                row.getUserId(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getFullName(),
                row.getGrantedBy(),
                row.getGrantedAt(),
                row.getReason());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
