package com.leadtracker.auth;

import com.leadtracker.config.AppProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and verifies the signed session token (HS256 JWT whose subject is the user id). */
@Service
public class JwtService {

  private final SecretKey key;
  private final Duration ttl;
  private final Clock clock;

  public JwtService(AppProperties props, Clock clock) {
    this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
    this.ttl = Duration.ofHours(props.sessionTtlHours());
    this.clock = clock;
  }

  public Duration ttl() {
    return ttl;
  }

  public String issue(UUID userId) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  /** The user id in a valid, unexpired token; empty for anything else. */
  public Optional<UUID> verify(String token) {
    try {
      String sub = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
      return Optional.ofNullable(sub).map(UUID::fromString);
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
