package com.fristagent.common.dto;

import java.time.Instant;

/**
 * 统一错误响应体。所有 4xx / 5xx 均返回此结构。
 */
public record ErrorResponse(
        String code,
        String message,
        String path,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, path, Instant.now());
    }
}
