package com.innowise.api_gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<String> handleServiceError(WebClientResponseException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(Objects.requireNonNullElse(ex.getHeaders()
                        .getContentType(), MediaType.APPLICATION_JSON))
                .body(ex.getResponseBodyAsString());
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ProblemDetail handleServiceUnavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Service is unavailable");
    }
}
