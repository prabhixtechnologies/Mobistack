package com.fixflow.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A caller's mistake must not be reported as ours. Production answered 500 to a
 * plain GET on a POST-only endpoint, which both misleads the client and logs a
 * stack trace at error level for every stray request or scanner hit.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void wrongMethodIsRejectedAsNotAllowed() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");

        ResponseEntity<ApiError> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.name());
        assertThat(response.getBody().path()).isEqualTo("/api/v1/auth/login");
    }

    /**
     * Without Allow, a 405 leaves the caller guessing what to send instead.
     */
    @Test
    void notAllowedNamesTheMethodsThatAre() {
        ResponseEntity<ApiError> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST", "PUT")),
                new MockHttpServletRequest("GET", "/api/v1/products"));

        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.POST, HttpMethod.PUT);
    }

    @Test
    void aMethodWithNoAlternativeStillAnswersCleanly() {
        ResponseEntity<ApiError> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("TRACE"),
                new MockHttpServletRequest("TRACE", "/api/v1/products"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).isEmpty();
    }

    @Test
    void wrongContentTypeIsRejectedAsUnsupported() {
        ResponseEntity<ApiError> response = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN,
                        List.of(MediaType.APPLICATION_JSON)),
                new MockHttpServletRequest("POST", "/api/v1/auth/login"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE.name());
    }
}
