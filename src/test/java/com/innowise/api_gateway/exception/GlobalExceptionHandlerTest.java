package com.innowise.api_gateway.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final URI SERVICE_URI = URI.create("http://localhost:8080/api/v1/users");

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleServiceError_shouldPassClientErrorAsIs() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"status\":409,\"detail\":\"User already exists\"}";
        WebClientResponseException ex = WebClientResponseException.create(HttpStatus.CONFLICT, "Conflict",
                headers, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, null);

        ResponseEntity<Object> response = handler.handleServiceError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isEqualTo(body);
    }

    @Test
    void handleServiceError_shouldReturn502_whenServiceFailsWithServerError() {
        WebClientResponseException ex = WebClientResponseException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error", new HttpHeaders(), "stack trace".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8, null);

        ResponseEntity<Object> response = handler.handleServiceError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class,
                problem -> assertThat(problem.getDetail()).isEqualTo("Service error"));
    }

    @Test
    void handleRequestError_shouldReturn503_whenServiceIsUnreachable() {
        ProblemDetail problem = handler.handleRequestError(requestError(new ConnectException("Connection refused")));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getDetail()).isEqualTo("Service is unavailable");
    }

    @Test
    void handleRequestError_shouldReturn504_whenServiceTimesOut() {
        ProblemDetail problem = handler.handleRequestError(requestError(ReadTimeoutException.INSTANCE));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT.value());
        assertThat(problem.getDetail()).isEqualTo("Service did not respond in time");
    }

    private WebClientRequestException requestError(Throwable cause) {
        return new WebClientRequestException(cause, HttpMethod.POST, SERVICE_URI, new HttpHeaders());
    }
}
