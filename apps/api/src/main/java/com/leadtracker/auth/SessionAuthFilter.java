package com.leadtracker.auth;

import com.leadtracker.user.User;
import com.leadtracker.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the session token from the {@code lt_session} cookie (or an {@code Authorization: Bearer} header for
 * scripts) and, if it belongs to an active user, marks the request as authenticated. The user is re-read on
 * every request so a deactivated account is locked out immediately.
 *
 * <p>Not a Spring bean on purpose: it is added to the security chain in {@code SecurityConfig} only, so the
 * servlet container doesn't register it a second time.
 */
public class SessionAuthFilter extends OncePerRequestFilter {

  /** Request attribute holding why authentication failed, for the 401 message. */
  public static final String FAILURE_ATTR = "auth.failure";

  private final JwtService jwt;
  private final UserRepository users;

  public SessionAuthFilter(JwtService jwt, UserRepository users) {
    this.jwt = jwt;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = readToken(request);
    if (token != null) {
      Optional<UUID> userId = jwt.verify(token);
      if (userId.isEmpty()) {
        request.setAttribute(FAILURE_ATTR, "Your session has expired. Sign in again.");
      } else {
        Optional<User> user = users.findById(userId.get());
        if (user.isEmpty() || !user.get().isActive()) {
          request.setAttribute(FAILURE_ATTR, "Your account is not active. Contact an admin.");
        } else {
          AuthUser principal = AuthUser.of(user.get());
          var auth =
              new UsernamePasswordAuthenticationToken(
                  principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      }
    }
    chain.doFilter(request, response);
  }

  private static String readToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie c : cookies) {
        if (SessionCookies.NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
          return c.getValue();
        }
      }
    }
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) return header.substring(7).trim();
    return null;
  }
}
