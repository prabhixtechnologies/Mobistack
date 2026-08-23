package com.fixflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error identifiers. Clients switch on these rather
 * than on message text, which is free to change or be localised.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    ALREADY_EXISTS(HttpStatus.CONFLICT),
    CONFLICT(HttpStatus.CONFLICT),
    STALE_RESOURCE(HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
    PRICE_BELOW_MINIMUM(HttpStatus.UNPROCESSABLE_ENTITY),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    WORKSPACE_REQUIRED(HttpStatus.FORBIDDEN),
    MEMBERSHIP_INACTIVE(HttpStatus.FORBIDDEN),
    NOT_A_MEMBER(HttpStatus.FORBIDDEN),
    INVITE_EXPIRED(HttpStatus.GONE),
    INVITE_USED(HttpStatus.CONFLICT),
    OTP_INVALID(HttpStatus.UNAUTHORIZED),
    PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    PAYMENT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    ENTITLEMENT_DENIED(HttpStatus.PAYMENT_REQUIRED),
    DEVICE_LIMIT_REACHED(HttpStatus.CONFLICT),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
