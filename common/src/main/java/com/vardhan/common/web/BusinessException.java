package com.vardhan.common.web;

import org.springframework.http.HttpStatus;

/**
 * A domain rule was violated (insufficient funds, wallet frozen, email taken...).
 * Carries the HTTP status and a stable machine-readable code for clients.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
