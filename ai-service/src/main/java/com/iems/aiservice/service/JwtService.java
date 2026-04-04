package com.iems.aiservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);

        String accountId = claims.get("accountId", String.class);
        if (accountId != null && !accountId.isBlank()) {
            return accountId;
        }

        String userId = claims.get("userId", String.class);
        if (userId != null && !userId.isBlank()) {
            return userId;
        }

        return claims.getSubject();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
