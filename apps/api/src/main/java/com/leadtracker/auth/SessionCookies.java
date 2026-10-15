package com.leadtracker.auth;

import com.leadtracker.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Writes and clears the httpOnly session cookie. */
@Component
public class SessionCookies {

  public static final String NAME = "lt_session";

  private final AppProperties props;

  public SessionCookies(AppProperties props) {
    this.props = props;
  }

  public void set(HttpServletResponse response, String token, Duration maxAge) {
    response.addHeader(HttpHeaders.SET_COOKIE, build(token, maxAge).toString());
  }

  public void clear(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
  }

  private ResponseCookie build(String value, Duration maxAge) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(props.cookieSecure())
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build();
  }
}
