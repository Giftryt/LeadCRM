package com.leadtracker.auth;

import com.leadtracker.common.ApiException;
import com.leadtracker.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Limits sign-in attempts per client IP within a 15-minute window. In-memory, so each API instance counts
 * separately; use a shared store (e.g. Redis) or the load balancer's rate limiting when running several.
 */
@Component
public class LoginRateLimiter {

  private static final Duration WINDOW = Duration.ofMinutes(15);

  private record Window(long startMillis, int count) {}

  private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
  private final int limit;
  private final Clock clock;

  public LoginRateLimiter(AppProperties props, Clock clock) {
    this.limit = props.loginRateLimit();
    this.clock = clock;
  }

  /** Records an attempt and throws 429 when the client is over the limit. */
  public void check(String clientIp) {
    long now = clock.millis();
    Window w =
        attempts.compute(
            clientIp,
            (k, cur) ->
                cur == null || now - cur.startMillis() >= WINDOW.toMillis()
                    ? new Window(now, 1)
                    : new Window(cur.startMillis(), cur.count() + 1));
    if (w.count() > limit) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many sign-in attempts. Wait 15 minutes and try again.");
    }
    if (attempts.size() > 10_000) {
      attempts.entrySet().removeIf(e -> now - e.getValue().startMillis() >= WINDOW.toMillis());
    }
  }
}
