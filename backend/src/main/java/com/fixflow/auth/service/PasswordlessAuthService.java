package com.fixflow.auth.service;

import com.fixflow.auth.domain.UserIdentity;
import com.fixflow.auth.domain.UserToken;
import com.fixflow.auth.dto.AuthDtos.AuthResponse;
import com.fixflow.auth.dto.AuthDtos.ChannelOtpRequest;
import com.fixflow.auth.dto.AuthDtos.EmailOtpVerifyRequest;
import com.fixflow.auth.dto.AuthDtos.EmailStartRequest;
import com.fixflow.auth.dto.AuthDtos.MagicLinkConsumeRequest;
import com.fixflow.auth.dto.AuthDtos.RegisterUserRequest;
import com.fixflow.auth.dto.AuthDtos.SsoDevRequest;
import com.fixflow.auth.repository.UserIdentityRepository;
import com.fixflow.auth.repository.UserTokenRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.security.jwt.JwtService;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordlessAuthService {

    private final UserRepository userRepository;
    private final UserTokenRepository userTokenRepository;
    private final UserIdentityRepository identityRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final FixFlowProperties properties;
    private final com.fixflow.notify.OtpDeliveryService otpDeliveryService;
    private final com.fixflow.notify.EmailDeliveryService emailDeliveryService;

    public record AuthMethods(List<String> methods, Map<String, Object> brand) {
    }

    public AuthMethods methods() {
        List<String> methods = new java.util.ArrayList<>(List.of(
                "PASSWORD", "MAGIC_LINK", "EMAIL_OTP", "PHONE", "WHATSAPP", "REGISTER"));
        if (properties.getAuth().isDevSsoEnabled()) {
            methods.add("SSO_DEV");
        }
        if (properties.getAuth().getGoogleClientId() != null && !properties.getAuth().getGoogleClientId().isBlank()) {
            methods.add("SSO_GOOGLE");
        }
        var brand = properties.getBrand();
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("organization", brand.getOrganization());
        card.put("product", brand.getProduct());
        card.put("tagline", brand.getTagline());
        card.put("copyrightYear", brand.getCopyrightYear());
        card.put("copyright", "© " + brand.getCopyrightYear() + " " + brand.getOrganization());
        return new AuthMethods(methods, card);
    }

    @Transactional
    public AuthResponse registerUser(RegisterUserRequest request, AuthService.ClientInfo client) {
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.alreadyExists("An account with this email already exists.");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email().toLowerCase());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerified(true);
        userRepository.save(user);
        return authService.sessionFor(user, client);
    }

    @Transactional
    public Map<String, String> sendMagicLink(EmailStartRequest request) {
        String token = jwtService.generateRefreshToken();
        UserToken row = new UserToken();
        row.setEmail(request.email().toLowerCase());
        userRepository.findWithRolesByEmail(request.email()).ifPresent(user -> row.setUserId(user.getId()));
        row.setTokenType(UserToken.MAGIC_LINK);
        row.setTokenHash(jwtService.hashRefreshToken(token));
        row.setExpiresAt(Instant.now().plus(Duration.ofMinutes(20)));
        userTokenRepository.save(row);
        String url = properties.getAuth().getWebOrigin() + properties.getAuth().getMagicLinkPath() + token;
        emailDeliveryService.send(null, row.getUserId(), "MAGIC_LINK", request.email(),
                "Your " + product() + " sign-in link",
                "Open this link to sign in. It expires in 20 minutes.\n\n" + url + "\n\n— " + product());
        return Map.of("status", "SENT");
    }

    @Transactional
    public AuthResponse consumeMagicLink(MagicLinkConsumeRequest request, AuthService.ClientInfo client) {
        UserToken row = requireFresh(UserToken.MAGIC_LINK, request.token());
        User user = resolveOrCreateEmailUser(row.getEmail(), row.getUserId(), displayNameFromEmail(row.getEmail()));
        row.setUsedAt(Instant.now());
        row.setUserId(user.getId());
        userTokenRepository.save(row);
        user.setEmailVerified(true);
        userRepository.save(user);
        return authService.sessionFor(user, client);
    }

    @Transactional
    public Map<String, String> sendEmailOtp(EmailStartRequest request) {
        String code = issueOtp();
        UserToken row = new UserToken();
        row.setEmail(request.email().toLowerCase());
        userRepository.findWithRolesByEmail(request.email()).ifPresent(user -> row.setUserId(user.getId()));
        row.setTokenType(UserToken.EMAIL_OTP);
        row.setTokenHash(jwtService.hashRefreshToken(code));
        row.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        userTokenRepository.save(row);
        emailDeliveryService.send(null, row.getUserId(), "EMAIL_OTP", request.email(),
                "Your " + product() + " code",
                "Your " + product() + " verification code is " + code + ". It expires in 10 minutes.\n\n— " + product());
        return Map.of("status", "SENT");
    }

    @Transactional
    public AuthResponse verifyEmailOtp(EmailOtpVerifyRequest request, AuthService.ClientInfo client) {
        UserToken row = requireFreshEmail(UserToken.EMAIL_OTP, request.code(), request.email());
        User user = resolveOrCreateEmailUser(request.email(), row.getUserId(), displayNameFromEmail(request.email()));
        row.setUsedAt(Instant.now());
        userTokenRepository.save(row);
        user.setEmailVerified(true);
        userRepository.save(user);
        return authService.sessionFor(user, client);
    }

    @Transactional
    public Map<String, String> sendPhoneOtp(ChannelOtpRequest request) {
        boolean whatsapp = request.channel() != null && request.channel().toUpperCase().contains("WHATSAPP");
        String phone = otpDeliveryService.normalizePhone(request.phone());
        var user = userRepository.findFirstByPhone(phone)
                .or(() -> userRepository.findFirstByPhone(request.phone()));
        var delivery = otpDeliveryService.sendPhone(phone, whatsapp, user.map(User::getId).orElse(null));
        UserToken row = new UserToken();
        row.setPhone(phone);
        row.setTokenType(whatsapp ? UserToken.WHATSAPP_OTP : UserToken.PHONE_OTP);
        row.setTokenHash(jwtService.hashRefreshToken(delivery.code()));
        row.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        user.ifPresent(found -> row.setUserId(found.getId()));
        userTokenRepository.save(row);
        return Map.of("status", "SENT", "channel", delivery.channel(), "provider", delivery.provider());
    }

    @Transactional
    public AuthResponse verifyPhoneOtp(String phone, String code, String channel, AuthService.ClientInfo client) {
        boolean whatsapp = channel != null && channel.toUpperCase().contains("WHATSAPP");
        String e164 = otpDeliveryService.normalizePhone(phone);
        UserToken row = requireFreshPhone(whatsapp ? UserToken.WHATSAPP_OTP : UserToken.PHONE_OTP, code, e164);
        User user = userRepository.findFirstByPhone(e164)
                .or(() -> userRepository.findFirstByPhone(phone))
                .orElseGet(() -> createPhoneUser(e164));
        row.setUsedAt(Instant.now());
        row.setUserId(user.getId());
        userTokenRepository.save(row);
        user.setPhoneVerified(true);
        user.setPhone(e164);
        userRepository.save(user);
        return authService.sessionFor(user, client);
    }

    @Transactional
    public AuthResponse ssoDev(SsoDevRequest request, AuthService.ClientInfo client) {
        if (!properties.getAuth().isDevSsoEnabled()) {
            throw ApiException.forbidden("SSO is not enabled in this environment.");
        }
        String provider = request.provider() == null || request.provider().isBlank() ? "DEV" : request.provider();
        String subject = request.email().toLowerCase();
        User user = identityRepository.findByProviderAndSubject(provider, subject)
                .flatMap(identity -> userRepository.findWithRolesById(identity.getUserId()))
                .orElseGet(() -> userRepository.findWithRolesByEmail(request.email())
                        .orElseGet(() -> createEmailUser(request.email(), request.fullName())));
        UserIdentity identity = identityRepository.findByProviderAndSubject(provider, subject)
                .orElseGet(UserIdentity::new);
        identity.setUserId(user.getId());
        identity.setProvider(provider);
        identity.setSubject(subject);
        identity.setEmail(request.email().toLowerCase());
        identityRepository.save(identity);
        user.setEmailVerified(true);
        userRepository.save(user);
        return authService.sessionFor(user, client);
    }

    public Map<String, String> googleStart(String requestedRedirect) {
        String clientId = properties.getAuth().getGoogleClientId();
        if (clientId == null || clientId.isBlank()) {
            return Map.of("provider", "GOOGLE", "status", "UNCONFIGURED");
        }
        String redirect = resolveGoogleRedirect(requestedRedirect);
        String url = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id="
                + clientId + "&redirect_uri=" + java.net.URLEncoder.encode(redirect, java.nio.charset.StandardCharsets.UTF_8)
                + "&scope=openid%20email%20profile&prompt=select_account";
        return Map.of("provider", "GOOGLE", "status", "READY", "authorizationUrl", url, "redirectUri", redirect);
    }

    @Transactional
    public AuthResponse googleConsume(com.fixflow.auth.dto.AuthDtos.GoogleCodeRequest request,
                                      AuthService.ClientInfo client) {
        String clientId = properties.getAuth().getGoogleClientId();
        String secret = properties.getAuth().getGoogleClientSecret();
        if (clientId == null || clientId.isBlank() || secret == null || secret.isBlank()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Google SSO is not configured.");
        }
        String redirect = resolveGoogleRedirect(request.redirectUri());
        var token = exchangeGoogleCode(request.code(), clientId, secret, redirect);
        var profile = googleProfile(token);
        if (profile.email() == null || profile.email().isBlank()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Google did not return an email address.");
        }
        String subject = profile.sub();
        User user = identityRepository.findByProviderAndSubject("GOOGLE", subject)
                .flatMap(identity -> userRepository.findWithRolesById(identity.getUserId()))
                .orElseGet(() -> userRepository.findWithRolesByEmail(profile.email())
                        .orElseGet(() -> createEmailUser(profile.email(), profile.name())));
        UserIdentity identity = identityRepository.findByProviderAndSubject("GOOGLE", subject)
                .orElseGet(UserIdentity::new);
        identity.setUserId(user.getId());
        identity.setProvider("GOOGLE");
        identity.setSubject(subject);
        identity.setEmail(profile.email().toLowerCase());
        identityRepository.save(identity);
        if (profile.name() != null && !profile.name().isBlank()) {
            user.setFullName(profile.name());
        }
        user.setEmailVerified(true);
        userRepository.save(user);
        return authService.sessionFor(user, client);
    }

    private String resolveGoogleRedirect(String requested) {
        String web = properties.getAuth().getWebOrigin() + properties.getAuth().getGoogleRedirectPath();
        String publicWeb = properties.getPlatform().getPublicOrigin() + properties.getAuth().getGoogleRedirectPath();
        if (requested == null || requested.isBlank()) {
            return web;
        }
        if (requested.equals(web) || requested.equals(publicWeb)
                || requested.startsWith("fixflow://")
                || requested.startsWith("mobistack://")
                || requested.startsWith(properties.getAuth().getWebOrigin() + "/")
                || requested.startsWith(properties.getPlatform().getPublicOrigin() + "/")) {
            return requested;
        }
        throw ApiException.forbidden("That Google redirect is not allowed.");
    }

    private String exchangeGoogleCode(String code, String clientId, String secret, String redirect) {
        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        form.add("redirect_uri", redirect);
        form.add("grant_type", "authorization_code");
        try {
            var body = org.springframework.web.client.RestClient.create()
                    .post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {
                    });
            Object access = body == null ? null : body.get("access_token");
            if (access == null) {
                throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Google did not return an access token.");
            }
            return access.toString();
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Google rejected that sign-in code.");
        }
    }

    private GoogleProfile googleProfile(String accessToken) {
        try {
            var body = org.springframework.web.client.RestClient.create()
                    .get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {
                    });
            if (body == null) {
                throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Google profile was empty.");
            }
            return new GoogleProfile(
                    String.valueOf(body.getOrDefault("sub", "")),
                    body.get("email") == null ? null : String.valueOf(body.get("email")),
                    body.get("name") == null ? displayNameFromEmail(String.valueOf(body.get("email")))
                            : String.valueOf(body.get("name")));
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "Google profile could not be loaded.");
        }
    }

    private record GoogleProfile(String sub, String email, String name) {
    }

    private UserToken requireFresh(String type, String raw) {
        UserToken row = userTokenRepository.findByTokenTypeAndTokenHash(type, jwtService.hashRefreshToken(raw))
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "That code is not valid."));
        if (row.getUsedAt() != null || row.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.OTP_INVALID, "That code has expired.");
        }
        return row;
    }

    private UserToken requireFreshEmail(String type, String raw, String email) {
        UserToken row = userTokenRepository
                .findFirstByTokenTypeAndTokenHashAndEmailIgnoreCaseAndUsedAtIsNull(
                        type, jwtService.hashRefreshToken(raw), email)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "That code is not valid."));
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.OTP_INVALID, "That code has expired.");
        }
        return row;
    }

    private UserToken requireFreshPhone(String type, String raw, String phone) {
        UserToken row = userTokenRepository
                .findFirstByTokenTypeAndTokenHashAndPhoneAndUsedAtIsNull(
                        type, jwtService.hashRefreshToken(raw), phone)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID, "That code is not valid."));
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.OTP_INVALID, "That code has expired.");
        }
        return row;
    }

    private User resolveOrCreateEmailUser(String email, UUID existingId, String name) {
        if (existingId != null) {
            return userRepository.findWithRolesById(existingId).orElseGet(() -> createEmailUser(email, name));
        }
        return userRepository.findWithRolesByEmail(email).orElseGet(() -> createEmailUser(email, name));
    }

    private User createEmailUser(String email, String name) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setFullName(name);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        return userRepository.save(user);
    }

    private User createPhoneUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setEmail("p" + phone.replaceAll("[^0-9]", "") + "@phone.prabhixtechnologies.local");
        user.setFullName(product() + " user");
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        return userRepository.save(user);
    }

    private String displayNameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return product() + " user";
        }
        String local = email.substring(0, email.indexOf('@')).replace('.', ' ');
        return local.isBlank() ? product() + " user" : local;
    }

    private String product() {
        return properties.getBrand().getProduct();
    }

    private String issueOtp() {
        return otpDeliveryService.issueCode();
    }
}
