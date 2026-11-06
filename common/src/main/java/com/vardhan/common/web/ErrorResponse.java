package com.vardhan.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Instant timestamp, Map<String, String> fieldErrors) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now(), null);
    }
}
