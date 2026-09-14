package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.commons.domain.CommonsReviewer;
import com.fixflow.commons.repository.CommonsReviewerRepository;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonsReviewerServiceTest {

    @Mock
    private CommonsReviewerRepository reviewers;
    @Mock
    private UserRepository users;

    private CommonsReviewerService service;
    private final UUID actor = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CommonsReviewerService(reviewers, users);
    }

    @Test
    void grantWritesTheUserIdAsPrimaryKey() {
        User user = new User();
        user.setId(target);
        user.setEmail("reviewer@prabhixtechnologies.com");
        user.setFullName("Reviewer");
        when(users.findById(target)).thenReturn(Optional.of(user));
        when(reviewers.findById(target)).thenReturn(Optional.empty());
        when(reviewers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CommonsReviewerService.ReviewerView view = service.grant(actor, target, "Trusted on the bench");

        assertThat(view.userId()).isEqualTo(target);
        assertThat(view.grantedBy()).isEqualTo(actor);
        assertThat(view.reason()).isEqualTo("Trusted on the bench");
        verify(reviewers).save(any(CommonsReviewer.class));
    }

    @Test
    void grantRefusesAnUnknownUser() {
        when(users.findById(target)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(actor, target, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void revokeDeletesTheRow() {
        service.revoke(target);
        verify(reviewers).deleteById(target);
    }
}
