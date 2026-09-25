package com.fixflow.auth.web;

import com.fixflow.auth.dto.AuthDtos.AuthenticatedUser;
import com.fixflow.auth.service.AuthService;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two things a client still asks this API about its session. Everything else about signing in
 * (credentials, OTP, password reset, SSO, device sessions) is Prabhix Identity's: the consoles send
 * people to its hosted pages and come back holding a bearer token.
 */
@RestController
@RequestMapping("/api/v1/mobistack/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    @GetMapping("/me")
    @Operation(summary = "The signed-in person: selected shop, roles, permissions and plan")
    public AuthenticatedUser me() {
        return authService.currentUser(CurrentUser.userId());
    }

    /**
     * Kept for clients that call it on sign-out. There is nothing local to end: the session belongs
     * to Identity, and the clients follow this call by sending the browser to its end-session page.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Acknowledges a sign-out; the session itself is ended at Identity")
    public void logout() {
    }
}
