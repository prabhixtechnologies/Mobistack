package com.fixflow.auth.web;

import com.fixflow.auth.dto.AuthDtos.AuthResponse;
import com.fixflow.auth.dto.AuthDtos.AuthenticatedUser;
import com.fixflow.auth.dto.AuthDtos.ChangePasswordRequest;
import com.fixflow.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.fixflow.auth.dto.AuthDtos.LoginRequest;
import com.fixflow.auth.dto.AuthDtos.OtpRequest;
import com.fixflow.auth.dto.AuthDtos.RefreshRequest;
import com.fixflow.auth.dto.AuthDtos.RegisterShopRequest;
import com.fixflow.auth.dto.AuthDtos.ResetPasswordRequest;
import com.fixflow.auth.dto.AuthDtos.ChannelOtpRequest;
import com.fixflow.auth.dto.AuthDtos.EmailOtpVerifyRequest;
import com.fixflow.auth.dto.AuthDtos.EmailStartRequest;
import com.fixflow.auth.dto.AuthDtos.MagicLinkConsumeRequest;
import com.fixflow.auth.dto.AuthDtos.RegisterUserRequest;
import com.fixflow.auth.dto.AuthDtos.GoogleCodeRequest;
import com.fixflow.auth.dto.AuthDtos.SsoDevRequest;
import com.fixflow.auth.dto.AuthDtos.VerifyOtpRequest;
import com.fixflow.auth.service.AuthService;
import com.fixflow.auth.service.DeviceSessionService;
import com.fixflow.auth.service.PasswordlessAuthService;
import com.fixflow.common.web.ClientRequests;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final PasswordlessAuthService passwordlessAuthService;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in and receive an access token plus a rotating refresh token")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientRequests.clientInfo(http, request.deviceId()));
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    @PostMapping("/request-otp")
    @SecurityRequirements
    public void requestOtp(@Valid @RequestBody OtpRequest request) {
        authService.requestOtp(request);
    }

    @PostMapping("/verify-otp")
    @SecurityRequirements
    public void verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
    }

    @GetMapping("/methods")
    @SecurityRequirements
    public PasswordlessAuthService.AuthMethods methods() {
        return passwordlessAuthService.methods();
    }

    @PostMapping("/register")
    @SecurityRequirements
    public AuthResponse registerUser(@Valid @RequestBody RegisterUserRequest request, HttpServletRequest http) {
        return passwordlessAuthService.registerUser(request, ClientRequests.clientInfo(http, null));
    }

    @PostMapping("/magic-link")
    @SecurityRequirements
    public java.util.Map<String, String> magicLink(@Valid @RequestBody EmailStartRequest request) {
        return passwordlessAuthService.sendMagicLink(request);
    }

    @PostMapping("/magic-link/consume")
    @SecurityRequirements
    public AuthResponse consumeMagic(@Valid @RequestBody MagicLinkConsumeRequest request, HttpServletRequest http) {
        return passwordlessAuthService.consumeMagicLink(request, ClientRequests.clientInfo(http, request.deviceId()));
    }

    @PostMapping("/email-otp")
    @SecurityRequirements
    public java.util.Map<String, String> emailOtp(@Valid @RequestBody EmailStartRequest request) {
        return passwordlessAuthService.sendEmailOtp(request);
    }

    @PostMapping("/email-otp/verify")
    @SecurityRequirements
    public AuthResponse verifyEmailOtp(@Valid @RequestBody EmailOtpVerifyRequest request, HttpServletRequest http) {
        return passwordlessAuthService.verifyEmailOtp(request, ClientRequests.clientInfo(http, null));
    }

    @PostMapping("/phone/start")
    @SecurityRequirements
    public java.util.Map<String, String> phoneStart(@Valid @RequestBody ChannelOtpRequest request) {
        return passwordlessAuthService.sendPhoneOtp(request);
    }

    @PostMapping("/phone/verify")
    @SecurityRequirements
    public AuthResponse phoneVerify(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest http) {
        return passwordlessAuthService.verifyPhoneOtp(request.phone(), request.code(), "SMS",
                ClientRequests.clientInfo(http, null));
    }

    @PostMapping("/whatsapp/start")
    @SecurityRequirements
    public java.util.Map<String, String> whatsappStart(@Valid @RequestBody ChannelOtpRequest request) {
        return passwordlessAuthService.sendPhoneOtp(new ChannelOtpRequest(request.phone(), "WHATSAPP"));
    }

    @PostMapping("/whatsapp/verify")
    @SecurityRequirements
    public AuthResponse whatsappVerify(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest http) {
        return passwordlessAuthService.verifyPhoneOtp(request.phone(), request.code(), "WHATSAPP",
                ClientRequests.clientInfo(http, null));
    }

    @PostMapping("/sso/dev")
    @SecurityRequirements
    public AuthResponse ssoDev(@Valid @RequestBody SsoDevRequest request, HttpServletRequest http) {
        return passwordlessAuthService.ssoDev(request, ClientRequests.clientInfo(http, request.deviceId()));
    }

    @GetMapping("/sso/google/start")
    @SecurityRequirements
    public java.util.Map<String, String> googleStart(@RequestParam(required = false) String redirectUri) {
        return passwordlessAuthService.googleStart(redirectUri);
    }

    @PostMapping("/sso/google")
    @SecurityRequirements
    public AuthResponse googleConsume(@Valid @RequestBody GoogleCodeRequest request, HttpServletRequest http) {
        return passwordlessAuthService.googleConsume(request, ClientRequests.clientInfo(http, request.deviceId()));
    }

    @PostMapping("/register-shop")
    @SecurityRequirements
    @Operation(summary = "Create a shop and its first OWNER account")
    public AuthResponse register(@Valid @RequestBody RegisterShopRequest request, HttpServletRequest http) {
        return authService.register(request, ClientRequests.clientInfo(http, null));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Rotate a refresh token and issue a new access token")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request, ClientRequests.clientInfo(http, request.deviceId()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the supplied refresh token")
    public void logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "The currently authenticated user")
    public AuthenticatedUser me() {
        return authService.currentUser(CurrentUser.userId());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the current user's password and end every other session")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.userId(), request);
    }

    @GetMapping("/sessions")
    @Operation(summary = "Active devices for this account")
    public List<DeviceSessionService.SessionCard> sessions(
            @RequestHeader(value = ClientRequests.DEVICE_HEADER, required = false) String deviceId) {
        return authService.sessions(CurrentUser.userId(), deviceId);
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Revoke one device session")
    public void revokeSession(@PathVariable UUID id) {
        authService.revokeSession(CurrentUser.userId(), id);
    }
}
