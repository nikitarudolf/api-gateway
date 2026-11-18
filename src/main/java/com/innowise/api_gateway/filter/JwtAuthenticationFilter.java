package com.innowise.api_gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.api_gateway.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> OPEN_PATHS = List.of(
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh");

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (OPEN_PATHS.contains(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)
                || !jwtService.isValidAccessToken(header.substring(BEARER_PREFIX.length()))) {
            return reject(exchange);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid or missing token");
        return response.writeWith(Mono.fromCallable(() -> objectMapper.writeValueAsBytes(problem))
                .map(response.bufferFactory()::wrap));
    }
}
