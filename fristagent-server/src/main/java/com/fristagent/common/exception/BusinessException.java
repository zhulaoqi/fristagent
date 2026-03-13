package com.fristagent.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务校验异常，携带业务错误码，默认映射到 HTTP 400。
 * 传入 {@link HttpStatus#CONFLICT}（409）可用于冲突场景。
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code   = code;
        this.status = status;
    }

    public String getCode()       { return code;   }
    public HttpStatus getStatus() { return status; }
}
