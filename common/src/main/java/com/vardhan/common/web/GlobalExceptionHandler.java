package com.vardhan.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into consistent JSON errors across all services.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fields.putIfAbsent(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", "Request validation failed", Instant.now(), fields));
    }

    /** Invalid query/path parameters, e.g. ?size=1000 when the maximum is 100. */
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleParameterValidation(Exception ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_PARAMETER", "One or more parameters are invalid"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_PARAMETER", "Parameter '" + ex.getName() + "' has an invalid value"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("MALFORMED_REQUEST", "Request body is missing or malformed"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("MISSING_HEADER", "Required header '" + ex.getHeaderName() + "' is missing"));
    }

    /**
     * Spring's own client errors (unknown URL, wrong HTTP method, unsupported content type...) carry their
     * status. Without this they fell through to the catch-all and came back as 500, as if the server had broken.
     */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, NoResourceFoundException.class,
            HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<ErrorResponse> handleSpringClientError(Exception ex) {
        HttpStatus status = ex instanceof org.springframework.web.ErrorResponse spring
                ? HttpStatus.resolve(spring.getStatusCode().value()) : null;
        if (status == null || status.is5xxServerError()) {
            return handleUnexpected(ex);
        }
        String message = switch (status) {
            case NOT_FOUND -> "No such endpoint";
            case METHOD_NOT_ALLOWED -> "This endpoint does not accept that HTTP method";
            case UNSUPPORTED_MEDIA_TYPE -> "Send the request body as application/json";
            default -> status.getReasonPhrase();
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(status.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "Something went wrong"));
    }
}
