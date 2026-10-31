package com.innowise.api_gateway.service;

import com.innowise.api_gateway.client.AuthServiceClient;
import com.innowise.api_gateway.client.UserServiceClient;
import com.innowise.api_gateway.dto.CredentialsRequest;
import com.innowise.api_gateway.dto.RegisterRequest;
import com.innowise.api_gateway.dto.TokenResponse;
import com.innowise.api_gateway.dto.UserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Service
public class RegistrationService {
    private final AuthServiceClient authServiceClient;
    private final UserServiceClient userServiceClient;

    public Mono<TokenResponse> register(RegisterRequest request) {
        CredentialsRequest credentials = new CredentialsRequest(request.email(), request.password());
        UserRequest userRequest = new UserRequest(request.name(),
                request.surname(), request.birthDate(), request.email());
        return authServiceClient.register(credentials)
                .then(authServiceClient.login(credentials))
                .flatMap(tokens -> userServiceClient.createUser(tokens.accessToken(), userRequest)
                        .onErrorResume(error -> rollback(tokens.accessToken(), error))
                        .thenReturn(tokens));
    }

    private Mono<Void> rollback(String accessToken, Throwable error) {
        log.warn("Profile creation failed, rolling back credentials: {}", error.getMessage());
        return authServiceClient.delete(accessToken)
                .onErrorResume(deleteError -> {
                    log.error("Failed to roll back credentials", deleteError);
                    return Mono.empty();
                })
                .then(Mono.error(error));
    }
}
