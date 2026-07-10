package com.cloud.polaris.common.api;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        int status,
        Instant timestamp,
        Map<String, Object> details
) {
    public static ErrorResponse of(String code, String message, int status) {
        return new ErrorResponse(code, message, status, Instant.now(), Map.of());
    }

    public static ErrorResponse of(String code, String message, int status, Map<String, Object> details) {
        return new ErrorResponse(code, message, status, Instant.now(), details);
    }
}
