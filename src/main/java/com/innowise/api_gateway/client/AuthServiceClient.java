package com.innowise.api_gateway.client;

import com.innowise.api_gateway.config.ServiceProperties;
import com.innowise.api_gateway.dto.CredentialsRequest;
import com.innowise.api_gateway.dto.TokenRequest;
import com.innowise.api_gateway.dto.TokenResponse;
import com.innowise.api_gateway.dto.ValidateResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AuthServiceClient {
    private static final String VALIDATE_PATH = "/api/v1/auth/validate";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String CREDENTIALS_PATH = "/api/v1/auth/credentials";

    private final WebClient webClient;

    public AuthServiceClient(WebClient.Builder builder, ServiceProperties properties) {
        this.webClient = builder.baseUrl(properties.authUrl()).build();
    }

    public Mono<ValidateResponse> validate(String token) {
        return webClient.post()
                .uri(VALIDATE_PATH)
                .bodyValue(new TokenRequest(token))
                .retrieve()
                .bodyToMono(ValidateResponse.class);
    }

    public Mono<Void> register(CredentialsRequest request) {
        return webClient.post()
                .uri(REGISTER_PATH)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<TokenResponse> login(CredentialsRequest request) {
        return webClient.post()
                .uri(LOGIN_PATH)
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
