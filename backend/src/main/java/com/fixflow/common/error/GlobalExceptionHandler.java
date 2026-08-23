package com.fixflow.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getCode();
        if (code.status().is5xxServerError()) {
            log.error("API error {} at {}", code, request.getRequestURI(), ex);
        } else {
            log.debug("API error {} at {}: {}", code, request.getRequestURI(), ex.getMessage());
        }
        ApiError body = new ApiError(Instant.now(), code.status().value(), code.name(),
                ex.getMessage(), request.getRequestURI(), List.of(), ex.getDetails());
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .toList();
        ApiError body = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_FAILED.name(), "Request validation failed",
                request.getRequestURI(), violations, Map.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage(),
                        v.getInvalidValue()))
                .toList();
        ApiError body = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_FAILED.name(), "Request validation failed",
                request.getRequestURI(), violations, Map.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformed(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(ErrorCode.MALFORMED_REQUEST, ex.getMessage(), request.getRequestURI()));
    }

    /**
     * A unique index we did not pre-check still fired. Surface it as a conflict
     * rather than a 500, but never leak the raw SQL to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Data integrity violation at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(ErrorCode.CONFLICT,
                        "This change conflicts with existing data. It may already exist.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(ErrorCode.STALE_RESOURCE,
                        "This record was changed by someone else. Reload and try again.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ErrorCode.FORBIDDEN,
                        "You do not have permission to perform this action.", request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ErrorCode.UNAUTHENTICATED, "Authentication required.", request.getRequestURI()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ErrorCode.NOT_FOUND, "No endpoint " + ex.getRequestURL(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR, "Something went wrong on our side.",
                        request.getRequestURI()));
    }
}
