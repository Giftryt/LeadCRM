package com.leadtracker.config;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application settings bound from the {@code app.*} keys in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String jwtSecret,
    int sessionTtlHours,
    String timezone,
    boolean cookieSecure,
    String corsOrigins,
    int loginRateLimit,
    Seed seed) {

  public AppProperties {
    if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("JWT_SECRET must be set and at least 32 characters long");
    }
    if (sessionTtlHours <= 0) sessionTtlHours = 12;
    if (timezone == null || timezone.isBlank()) timezone = "Asia/Kolkata";
    ZoneId.of(timezone); // fail fast on an unknown zone
    if (loginRateLimit <= 0) loginRateLimit = 20;
    if (seed == null) seed = new Seed(null, null, "Admin", false);
  }

  public ZoneId zone() {
    return ZoneId.of(timezone);
  }

  public List<String> corsOriginList() {
    if (corsOrigins == null || corsOrigins.isBlank()) return List.of();
    return Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  public record Seed(String adminEmail, String adminPassword, String adminName, boolean sampleLeads) {}
}
