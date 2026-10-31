package com.innowise.api_gateway.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Object> handleServiceError(WebClientResponseException ex) {
        if (ex.getStatusCode().is5xxServerError()) {
            log.error("Service returned {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Service error"));
        }
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(Objects.requireNonNullElse(ex.getHeaders()
                        .getContentType(), MediaType.APPLICATION_JSON))
                .body(ex.getResponseBodyAsString());
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ProblemDetail handleRequestError(WebClientRequestException ex) {
        if (ex.getCause() instanceof ReadTimeoutException) {
            log.warn("Service did not respond in time: {}", ex.getUri());
            return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, "Service did not respond in time");
        }
        log.warn("Service is unavailable: {}", ex.getUri());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Service is unavailable");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidation(WebExchangeBindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
