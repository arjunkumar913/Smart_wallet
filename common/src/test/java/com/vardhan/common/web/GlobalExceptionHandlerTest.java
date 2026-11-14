package com.vardhan.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void wrongHttpMethod_is405_not500() {
        ResponseEntity<ErrorResponse> response = handler.handleSpringClientError(
                new HttpRequestMethodNotSupportedException("POST"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unknownUrl_is404_not500() {
        ResponseEntity<ErrorResponse> response = handler.handleSpringClientError(
                new NoResourceFoundException(HttpMethod.GET, "api/nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }
}
