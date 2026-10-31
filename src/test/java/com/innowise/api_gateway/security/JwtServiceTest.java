package com.innowise.api_gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final SecretKey key = Jwts.SIG.HS256.key().build();
    private final JwtService jwtService = new JwtService(new JwtProperties(Encoders.BASE64.encode(key.getEncoded())));

    @Test
    void isValidAccessToken_shouldReturnTrue_whenAccessTokenIsValid() {
        assertThat(jwtService.isValidAccessToken(token(key, "access", Instant.now().plusSeconds(60)))).isTrue();
    }

    @Test
    void isValidAccessToken_shouldReturnFalse_whenRefreshTokenIsUsed() {
        assertThat(jwtService.isValidAccessToken(token(key, "refresh", Instant.now().plusSeconds(60)))).isFalse();
    }

    @Test
    void isValidAccessToken_shouldReturnFalse_whenTokenIsExpired() {
        assertThat(jwtService.isValidAccessToken(token(key, "access", Instant.now().minusSeconds(60)))).isFalse();
    }

    @Test
    void isValidAccessToken_shouldReturnFalse_whenSignedWithAnotherKey() {
        SecretKey otherKey = Jwts.SIG.HS256.key().build();

        assertThat(jwtService.isValidAccessToken(token(otherKey, "access", Instant.now().plusSeconds(60)))).isFalse();
    }

    @Test
    void isValidAccessToken_shouldReturnFalse_whenTokenIsMalformed() {
        assertThat(jwtService.isValidAccessToken("not-a-jwt")).isFalse();
        assertThat(jwtService.isValidAccessToken("")).isFalse();
    }

    private String token(SecretKey signingKey, String type, Instant expiration) {
        return Jwts.builder()
                .subject("1")
                .claim("type", type)
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }
}
