package com.fixflow.security.jwt;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.security.Permission;
import com.fixflow.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getSecurity().getJwt()
                .setSecret("fixflow-test-signing-key-that-is-definitely-longer-than-sixty-four-chars");
        properties.getSecurity().getJwt().setAccessTokenTtl(Duration.ofMinutes(5));
        jwtService = new JwtService(properties);
        jwtService.init();

        principal = new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), "owner@prabhixtechnologies.com",
                "Abhishek Sharma", true, Set.of("OWNER"), Set.of(Permission.INVENTORY_READ, Permission.SALES_WRITE));
    }

    @Test
    void roundTripsThePrincipal() {
        String token = jwtService.createAccessToken(principal);
        UserPrincipal parsed = jwtService.parseAccessToken(token);

        assertThat(parsed.getId()).isEqualTo(principal.getId());
        assertThat(parsed.getShopId()).isEqualTo(principal.getShopId());
        assertThat(parsed.getEmail()).isEqualTo("owner@prabhixtechnologies.com");
        assertThat(parsed.getRoles()).containsExactly("OWNER");
        assertThat(parsed.has(Permission.SALES_WRITE)).isTrue();
        assertThat(parsed.has(Permission.USER_WRITE)).isFalse();
    }

    @Test
    void rejectsATamperedToken() {
        String token = jwtService.createAccessToken(principal);
        assertThatThrownBy(() -> jwtService.parseAccessToken(token + "x"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void emptyShopClaimMeansNoWorkspaceSelected() {
        UserPrincipal unscoped = new UserPrincipal(principal.getId(), null, principal.getEmail(),
                principal.getFullName(), true, Set.of(), Set.of());

        UserPrincipal parsed = jwtService.parseAccessToken(jwtService.createAccessToken(unscoped));

        assertThat(parsed.getShopId()).isNull();
        assertThat(parsed.hasWorkspace()).isFalse();
        assertThat(parsed.getRoles()).isEmpty();
    }

    @Test
    void hashesRefreshTokensConsistently() {
        String raw = jwtService.generateRefreshToken();
        assertThat(jwtService.hashRefreshToken(raw)).isEqualTo(jwtService.hashRefreshToken(raw));
        assertThat(jwtService.hashRefreshToken(raw)).isNotEqualTo(raw);
    }
}
