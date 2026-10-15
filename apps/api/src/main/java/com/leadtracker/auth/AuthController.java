package com.leadtracker.auth;

import com.leadtracker.common.ApiException;
import com.leadtracker.user.User;
import com.leadtracker.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  public record LoginRequest(String email, String password) {}

  public record MeResponse(AuthUser user) {}

  public record LoginResponse(AuthUser user, String token) {}

  public record ChangePasswordRequest(String currentPassword, String newPassword) {}

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final SessionCookies cookies;
  private final LoginRateLimiter limiter;
  private final Clock clock;

  public AuthController(
      UserRepository users,
      PasswordEncoder encoder,
      JwtService jwt,
      SessionCookies cookies,
      LoginRateLimiter limiter,
      Clock clock) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
    this.cookies = cookies;
    this.limiter = limiter;
    this.clock = clock;
  }

  @PostMapping("/login")
  public LoginResponse login(
      @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
    limiter.check(request.getRemoteAddr());
    String email = body.email() == null ? "" : body.email().trim().toLowerCase(Locale.ROOT);
    if (email.isEmpty() || !email.contains("@")) throw ApiException.validation("email", "is not a valid email address");
    if (body.password() == null || body.password().isEmpty()) throw ApiException.validation("password", "is required");

    User user = users.findByEmail(email).orElse(null);
    // Same message for an unknown email and a wrong password, so accounts can't be discovered.
    if (user == null || !encoder.matches(body.password(), user.getPasswordHash())) {
      throw ApiException.unauthorized("Email or password is incorrect.");
    }
    if (!user.isActive()) throw ApiException.unauthorized("Your account is not active. Contact an admin.");

    String token = jwt.issue(user.getId());
    cookies.set(response, token, jwt.ttl());
    return new LoginResponse(AuthUser.of(user), token);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    cookies.clear(response);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AuthUser user) {
    return new MeResponse(user);
  }

  @PostMapping("/change-password")
  @Transactional
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AuthUser me, @RequestBody ChangePasswordRequest body) {
    if (body.currentPassword() == null || body.currentPassword().isEmpty()) {
      throw ApiException.validation("currentPassword", "is required");
    }
    Passwords.validate(body.newPassword(), "newPassword");
    User user = users.findById(me.id()).orElseThrow(() -> ApiException.unauthorized("Sign in to continue."));
    if (!encoder.matches(body.currentPassword(), user.getPasswordHash())) {
      throw ApiException.badRequest("Current password is incorrect.");
    }
    user.setPasswordHash(encoder.encode(body.newPassword()));
    user.setUpdatedAt(clock.instant());
    users.save(user);
    return ResponseEntity.noContent().build();
  }
}
