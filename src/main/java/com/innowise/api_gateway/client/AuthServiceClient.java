package com.innowise.api_gateway.client;

import com.innowise.api_gateway.config.ServiceProperties;
import com.innowise.api_gateway.dto.CredentialsRequest;
import com.innowise.api_gateway.dto.TokenResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AuthServiceClient {
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String CREDENTIALS_PATH = "/api/v1/auth/credentials";

    private final WebClient webClient;

    public AuthServiceClient(WebClient.Builder builder, ServiceProperties properties) {
        this.webClient = builder.baseUrl(properties.authUrl()).build();
    }

    public Mono<TokenResponse> register(CredentialsRequest request) {
        return webClient.post()
                .uri(REGISTER_PATH)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TokenResponse.class);
    }

    public Mono<Void> delete(String accessToken) {
        return webClient.delete()
                .uri(CREDENTIALS_PATH)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(Void.class);
    }
}
