package com.innowise.api_gateway.service;

import com.innowise.api_gateway.client.AuthServiceClient;
import com.innowise.api_gateway.client.UserServiceClient;
import com.innowise.api_gateway.dto.CredentialsRequest;
import com.innowise.api_gateway.dto.RegisterRequest;
import com.innowise.api_gateway.dto.TokenResponse;
import com.innowise.api_gateway.dto.UserRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final String EMAIL = "ivan@example.com";
    private static final String PASSWORD = "password123";
    private static final String ACCESS_TOKEN = "access-token";
    private static final LocalDate BIRTH_DATE = LocalDate.of(2000, 1, 1);

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private RegistrationService registrationService;

    private final RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, "Ivan", "Petrov", BIRTH_DATE);
    private final CredentialsRequest credentials = new CredentialsRequest(EMAIL, PASSWORD);
    private final UserRequest userRequest = new UserRequest("Ivan", "Petrov", BIRTH_DATE, EMAIL);
    private final TokenResponse tokens = new TokenResponse(ACCESS_TOKEN, "refresh-token");

    @Test
    void register_shouldReturnTokens_whenAllStepsSucceed() {
        when(authServiceClient.register(credentials)).thenReturn(Mono.empty());
        when(authServiceClient.login(credentials)).thenReturn(Mono.just(tokens));
        when(userServiceClient.createUser(ACCESS_TOKEN, userRequest)).thenReturn(Mono.empty());

        StepVerifier.create(registrationService.register(request))
                .expectNext(tokens)
                .verifyComplete();

        verify(authServiceClient, never()).delete(any());
    }

    @Test
    void register_shouldDeleteCredentialsAndReturnOriginalError_whenProfileCreationFails() {
        WebClientResponseException profileError = errorResponse(HttpStatus.BAD_REQUEST);
        when(authServiceClient.register(credentials)).thenReturn(Mono.empty());
        when(authServiceClient.login(credentials)).thenReturn(Mono.just(tokens));
        when(userServiceClient.createUser(ACCESS_TOKEN, userRequest)).thenReturn(Mono.error(profileError));
        when(authServiceClient.delete(ACCESS_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(registrationService.register(request))
                .expectErrorMatches(error -> error == profileError)
                .verify();

        verify(authServiceClient).delete(ACCESS_TOKEN);
    }

    @Test
    void register_shouldReturnOriginalError_whenRollbackAlsoFails() {
        WebClientResponseException profileError = errorResponse(HttpStatus.BAD_REQUEST);
        when(authServiceClient.register(credentials)).thenReturn(Mono.empty());
        when(authServiceClient.login(credentials)).thenReturn(Mono.just(tokens));
        when(userServiceClient.createUser(ACCESS_TOKEN, userRequest)).thenReturn(Mono.error(profileError));
        when(authServiceClient.delete(ACCESS_TOKEN)).thenReturn(Mono.error(new IllegalStateException("Delete failed")));

        StepVerifier.create(registrationService.register(request))
                .expectErrorMatches(error -> error == profileError)
                .verify();
    }

    @Test
    void register_shouldNotLoginOrRollback_whenCredentialsRegistrationFails() {
        WebClientResponseException conflict = errorResponse(HttpStatus.CONFLICT);
        PublisherProbe<TokenResponse> login = PublisherProbe.of(Mono.just(tokens));
        when(authServiceClient.register(credentials)).thenReturn(Mono.error(conflict));
        when(authServiceClient.login(credentials)).thenReturn(login.mono());

        StepVerifier.create(registrationService.register(request))
                .expectErrorMatches(error -> error == conflict)
                .verify();

        login.assertWasNotSubscribed();
        verify(userServiceClient, never()).createUser(any(), any());
        verify(authServiceClient, never()).delete(any());
    }

    private WebClientResponseException errorResponse(HttpStatus status) {
        return WebClientResponseException.create(status, status.getReasonPhrase(), new HttpHeaders(), new byte[0], null, null);
    }
}
