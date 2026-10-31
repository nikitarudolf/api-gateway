package com.innowise.api_gateway.client;

import com.innowise.api_gateway.config.ServiceProperties;
import com.innowise.api_gateway.dto.UserRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class UserServiceClient {
    private static final String USER_PATH = "/api/v1/users";

    private final WebClient webClient;

    public UserServiceClient(WebClient.Builder builder, ServiceProperties properties) {
        this.webClient = builder.baseUrl(properties.userUrl()).build();
    }

    public Mono<Void> createUser(String accessToken, UserRequest request) {
        return webClient.post()
                .uri(USER_PATH)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
