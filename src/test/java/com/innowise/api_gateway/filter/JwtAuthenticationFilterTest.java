package com.innowise.api_gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.api_gateway.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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
    private JwtService jwtService;

    @Mock
    private WebFilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, new ObjectMapper());
    }

    @Test
    void filter_shouldPassWithoutValidation_whenPathIsOpen() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/register"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(jwtService);
    }

    @Test
    void filter_shouldReturn401WithProblemDetail_whenAuthorizationHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(USERS_PATH));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("\"status\":401", "Invalid or missing token"))
                .verifyComplete();
        verifyNoInteractions(jwtService, chain);
    }

    @Test
    void filter_shouldReturn401_whenAuthorizationIsNotBearer() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Basic dXNlcjpwYXNz");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtService, chain);
    }

    @Test
    void filter_shouldReturn401_whenTokenIsInvalid() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Bearer " + TOKEN);
        when(jwtService.isValidAccessToken(TOKEN)).thenReturn(false);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_shouldPassRequest_whenTokenIsValid() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Bearer " + TOKEN);
        when(jwtService.isValidAccessToken(TOKEN)).thenReturn(true);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private MockServerWebExchange exchangeWithAuthorization(String authorization) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(USERS_PATH)
                .header(HttpHeaders.AUTHORIZATION, authorization));
    }
}
