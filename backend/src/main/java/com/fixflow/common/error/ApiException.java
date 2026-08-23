package com.fixflow.common.error;

import lombok.Getter;

import java.util.Map;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public static ApiException notFound(String entity, Object id) {
        return new ApiException(ErrorCode.NOT_FOUND, entity + " not found: " + id);
    }

    public static ApiException alreadyExists(String message) {
        return new ApiException(ErrorCode.ALREADY_EXISTS, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException businessRule(String message) {
        return new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }
}
