package com.diladrive.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.diladrive.user.Role;
import com.diladrive.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration lifetime;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret()));
        this.lifetime = Duration.ofMinutes(props.expirationMinutes());
    }

    public long lifetimeSeconds() {
        return lifetime.toSeconds();
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /** Returns the identity inside a valid, unexpired token, or empty if the token is invalid. */
    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            Role role = Role.valueOf(claims.get("role", String.class));
            return Optional.of(new AuthUser(userId, role));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
