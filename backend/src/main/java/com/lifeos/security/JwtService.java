package com.lifeos.security;

import com.lifeos.config.JwtProperties;
import com.lifeos.exception.AppException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Issues and validates signed access tokens. Signature, issuer and expiry are all verified. */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "lifeos.jwt.secret must be configured with at least 32 characters. Set the JWT_SECRET environment variable.");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issueAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .issuer(properties.issuer())
                .subject(principal.id())
                .claim("email", principal.email())
                .claim("role", principal.role().name())
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, now, expiry);
    }

    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"access".equals(claims.get("typ", String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public String newRefreshTokenValue() {
        return UUID.randomUUID() + "." + UUID.randomUUID() + "." + UUID.randomUUID();
    }

    public Instant refreshExpiry() {
        return Instant.now().plus(properties.refreshTokenTtl());
    }

    public void assertParsable(String token) {
        if (parse(token).isEmpty()) {
            throw AppException.unauthorized("Token is invalid or has expired");
        }
    }

    public record IssuedToken(String value, String jti, Instant issuedAt, Instant expiresAt) {
    }
}