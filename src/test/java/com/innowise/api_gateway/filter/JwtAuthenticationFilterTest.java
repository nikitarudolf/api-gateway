package com.innowise.api_gateway.filter;

import com.innowise.api_gateway.client.AuthServiceClient;
import com.innowise.api_gateway.dto.ValidateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String USERS_PATH = "/api/v1/users";
    private static final String TOKEN = "access-token";

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private GatewayFilterChain chain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Test
    void filter_shouldPassWithoutValidation_whenPathIsOpen() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(authServiceClient);
    }

    @Test
    void filter_shouldReturn401_whenAuthorizationHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(USERS_PATH));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(authServiceClient, chain);
    }

    @Test
    void filter_shouldReturn401_whenAuthorizationIsNotBearer() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Basic dXNlcjpwYXNz");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(authServiceClient, chain);
    }

    @Test
    void filter_shouldReturn401_whenTokenIsBlank() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Bearer   ");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(authServiceClient, chain);
    }

    @Test
    void filter_shouldPassRequest_whenTokenIsValid() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Bearer " + TOKEN);
        when(authServiceClient.validate(TOKEN)).thenReturn(Mono.just(new ValidateResponse(1L, "USER")));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void filter_shouldReturn401_whenAuthServiceRejectsToken() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Bearer " + TOKEN);
        WebClientResponseException unauthorized = WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", new HttpHeaders(), new byte[0], null, null);
        when(authServiceClient.validate(TOKEN)).thenReturn(Mono.error(unauthorized));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_shouldReturn503_whenAuthServiceIsUnavailable() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Bearer " + TOKEN);
        WebClientRequestException unavailable = new WebClientRequestException(
                new ConnectException("Connection refused"), HttpMethod.POST,
                URI.create("http://localhost:8081/api/v1/auth/validate"), new HttpHeaders());
        when(authServiceClient.validate(TOKEN)).thenReturn(Mono.error(unavailable));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(any());
    }

    private MockServerWebExchange exchangeWithAuthorization(String authorization) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(USERS_PATH)
                .header(HttpHeaders.AUTHORIZATION, authorization));
    }
}
