package com.fixflow.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(name = "ApiError", description = "Uniform error envelope returned by every FixFlow endpoint")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> violations,
        Map<String, Object> details
) {

    public record FieldViolation(String field, String message, Object rejectedValue) {
    }

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, List.of(), Map.of());
    }
}
