package com.innowise.api_gateway.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.api_gateway.dto.RegisterRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String USERS_PATH = "/api/v1/users";
    private static final String EMAIL = "ivan@example.com";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String TOKENS_JSON = """
            {"accessToken":"access-token","refreshToken":"refresh-token"}""";

    private static MockWebServer authServer;
    private static MockWebServer userServer;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startServers() throws IOException {
        authServer = new MockWebServer();
        authServer.start();
        userServer = new MockWebServer();
        userServer.start();
    }

    @AfterAll
    static void stopServers() throws IOException {
        authServer.shutdown();
        userServer.shutdown();
    }

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("services.auth-url", () -> "http://localhost:" + authServer.getPort());
        registry.add("services.user-url", () -> "http://localhost:" + userServer.getPort());
    }

    @Test
    void register_shouldReturn201AndTokens_whenAllServicesSucceed() throws Exception {
        authServer.enqueue(jsonResponse(201, "{\"id\":1,\"email\":\"" + EMAIL + "\",\"role\":\"USER\"}"));
        authServer.enqueue(jsonResponse(200, TOKENS_JSON));
        userServer.enqueue(jsonResponse(201, "{\"id\":1}"));

        webTestClient.post().uri(REGISTER_PATH)
                .bodyValue(registerRequest("Ivan"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo(ACCESS_TOKEN)
                .jsonPath("$.refreshToken").isEqualTo("refresh-token");

        RecordedRequest register = takeRequest(authServer);
        assertThat(register.getPath()).isEqualTo(REGISTER_PATH);
        assertThat(body(register).get("email").asText()).isEqualTo(EMAIL);
        assertThat(takeRequest(authServer).getPath()).isEqualTo("/api/v1/auth/login");

        RecordedRequest createUser = takeRequest(userServer);
        assertThat(createUser.getPath()).isEqualTo(USERS_PATH);
        assertThat(createUser.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + ACCESS_TOKEN);
        assertThat(body(createUser).get("name").asText()).isEqualTo("Ivan");
        assertThat(body(createUser).get("birthDate").asText()).isEqualTo("2000-01-01");
    }

    @Test
    void register_shouldRollbackAndReturnUserServiceError_whenProfileCreationFails() throws Exception {
        authServer.enqueue(jsonResponse(201, "{\"id\":1}"));
        authServer.enqueue(jsonResponse(200, TOKENS_JSON));
        authServer.enqueue(new MockResponse().setResponseCode(204));
        userServer.enqueue(problemResponse(400, "{\"status\":400,\"errors\":{\"name\":\"must not be blank\"}}"));

        webTestClient.post().uri(REGISTER_PATH)
                .bodyValue(registerRequest(""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.name").isEqualTo("must not be blank");

        takeRequest(authServer);
        takeRequest(authServer);
        takeRequest(userServer);
        RecordedRequest rollback = takeRequest(authServer);
        assertThat(rollback.getMethod()).isEqualTo("DELETE");
        assertThat(rollback.getPath()).isEqualTo("/api/v1/auth/credentials");
        assertThat(rollback.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + ACCESS_TOKEN);
    }

    @Test
    void register_shouldReturn409WithoutCallingUserService_whenEmailAlreadyExists() throws Exception {
        int authRequestsBefore = authServer.getRequestCount();
        int userRequestsBefore = userServer.getRequestCount();
        authServer.enqueue(problemResponse(409, "{\"status\":409,\"detail\":\"User already exists\"}"));

        webTestClient.post().uri(REGISTER_PATH)
                .bodyValue(registerRequest("Ivan"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("User already exists");

        assertThat(takeRequest(authServer).getPath()).isEqualTo(REGISTER_PATH);
        assertThat(authServer.getRequestCount()).isEqualTo(authRequestsBefore + 1);
        assertThat(userServer.getRequestCount()).isEqualTo(userRequestsBefore);
    }

    @Test
    void protectedRoute_shouldReturn401WithoutCallingServices_whenTokenIsMissing() {
        int authRequestsBefore = authServer.getRequestCount();
        int userRequestsBefore = userServer.getRequestCount();

        webTestClient.get().uri(USERS_PATH)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(authServer.getRequestCount()).isEqualTo(authRequestsBefore);
        assertThat(userServer.getRequestCount()).isEqualTo(userRequestsBefore);
    }

    @Test
    void protectedRoute_shouldReturn401_whenAuthServiceRejectsToken() throws Exception {
        int userRequestsBefore = userServer.getRequestCount();
        authServer.enqueue(problemResponse(401, "{\"status\":401}"));

        webTestClient.get().uri(USERS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(takeRequest(authServer).getPath()).isEqualTo("/api/v1/auth/validate");
        assertThat(userServer.getRequestCount()).isEqualTo(userRequestsBefore);
    }

    @Test
    void protectedRoute_shouldProxyToUserService_whenTokenIsValid() throws Exception {
        authServer.enqueue(jsonResponse(200, "{\"userId\":1,\"role\":\"ADMIN\"}"));
        userServer.enqueue(jsonResponse(200, "[{\"id\":1,\"name\":\"Ivan\"}]"));

        webTestClient.get().uri(USERS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("Ivan");

        RecordedRequest validate = takeRequest(authServer);
        assertThat(validate.getPath()).isEqualTo("/api/v1/auth/validate");
        assertThat(body(validate).get("token").asText()).isEqualTo(ACCESS_TOKEN);

        RecordedRequest proxied = takeRequest(userServer);
        assertThat(proxied.getPath()).isEqualTo(USERS_PATH);
        assertThat(proxied.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + ACCESS_TOKEN);
    }

    @Test
    void internalAuthEndpoint_shouldReturn404() {
        webTestClient.post().uri("/api/v1/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"x\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    private RegisterRequest registerRequest(String name) {
        return new RegisterRequest(EMAIL, "password123", name, "Petrov", LocalDate.of(2000, 1, 1));
    }

    private MockResponse jsonResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private MockResponse problemResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .setBody(body);
    }

    private RecordedRequest takeRequest(MockWebServer server) throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        return request;
    }

    private JsonNode body(RecordedRequest request) throws IOException {
        return objectMapper.readTree(request.getBody().clone().readUtf8());
    }
}
