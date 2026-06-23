package com.iems.aiservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private JwtService jwtService;
    private String secret;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        secret = java.util.Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());
        ReflectionTestUtils.setField(jwtService, "secretKey", secret);
    }

    @Test
    void shouldPreferAccountIdWhenPresent() {
        UUID userId = UUID.randomUUID();
        String token = tokenWithClaims("alice", userId.toString(), "other-user");

        assertEquals(userId.toString(), jwtService.extractUserId(token));
    }

    @Test
    void shouldFallbackToUserIdWhenAccountIdMissing() {
        UUID userId = UUID.randomUUID();
        String token = tokenWithClaims("alice", null, userId.toString());

        assertEquals(userId.toString(), jwtService.extractUserId(token));
    }

    @Test
    void shouldFallbackToSubjectWhenNoIdClaimsArePresent() {
        String token = tokenWithClaims("alice", null, null);

        assertEquals("alice", jwtService.extractUserId(token));
    }

    @Test
    void malformedTokenShouldReturnFalseForTokenTypeChecks() {
        assertThrows(RuntimeException.class, () -> jwtService.extractUserId("bad.token.value"));
    }

    private String tokenWithClaims(String subject, String accountId, String userId) {
        Instant now = Instant.now();
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

        JwtBuilder builder = Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now));

        if (accountId != null) {
            builder.claim("accountId", accountId);
        }
        if (userId != null) {
            builder.claim("userId", userId);
        }

        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }
}