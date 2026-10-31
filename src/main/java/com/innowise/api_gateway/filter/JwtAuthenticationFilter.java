package com.innowise.api_gateway.filter;

import com.innowise.api_gateway.client.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> OPEN_PATHS = List.of("/api/v1/auth/login", "/api/v1/auth/refresh");

    private final AuthServiceClient authServiceClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (OPEN_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        String token = header.substring(BEARER_PREFIX.length());
        if (token.isBlank()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        return authServiceClient.validate(token)
                .flatMap(response -> chain.filter(exchange))
                .onErrorResume(WebClientResponseException.Unauthorized.class,
                        e -> reject(exchange, HttpStatus.UNAUTHORIZED))
                .onErrorResume(WebClientRequestException.class,
                        e -> reject(exchange, HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
}
