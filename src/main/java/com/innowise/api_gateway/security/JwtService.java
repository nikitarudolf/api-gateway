package com.innowise.api_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtParser parser;

    public JwtService(JwtProperties properties) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret())))
                .build();
    }

    public boolean isValidAccessToken(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            return ACCESS_TOKEN_TYPE.equals(claims.get(TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
