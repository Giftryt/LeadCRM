package com.leadtracker.user;

import com.leadtracker.auth.AuthUser;
import com.leadtracker.auth.Passwords;
import com.leadtracker.common.ApiException;
import com.leadtracker.common.Ids;
import com.leadtracker.common.Items;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

  static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  public record UserDto(UUID id, String email, String name, Role role, boolean active, Instant createdAt) {
    static UserDto of(User u) {
      return new UserDto(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.isActive(), u.getCreatedAt());
    }
  }

  public record CreateUserRequest(String name, String email, Role role, String password) {}

  public record UpdateUserRequest(String name, Role role, Boolean active, String password) {}

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final Clock clock;

  public UserController(UserRepository users, PasswordEncoder encoder, Clock clock) {
    this.users = users;
    this.encoder = encoder;
    this.clock = clock;
  }

  /** Everyone signed in can list active users (for owner pickers). Admins may include inactive ones. */
  @GetMapping
  public Items<UserDto> list(
      @AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "false") boolean includeInactive) {
    List<User> list =
        includeInactive && me.isAdmin() ? users.findAllByOrderByNameAsc() : users.findByActiveTrueOrderByNameAsc();
    return new Items<>(list.stream().map(UserDto::of).toList());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public UserDto create(@AuthenticationPrincipal AuthUser me, @RequestBody CreateUserRequest body) {
    me.requireAdmin();
    String name = requireName(body.name());
    String email = body.email() == null ? "" : body.email().trim().toLowerCase(Locale.ROOT);
    if (!EMAIL.matcher(email).matches()) throw ApiException.validation("email", "is not a valid email address");
    Passwords.validate(body.password(), "password");
    if (users.existsByEmail(email)) throw ApiException.conflict("A user with that email already exists.");
    Role role = body.role() == null ? Role.REP : body.role();
    User user = new User(email, name, encoder.encode(body.password()), role, clock.instant());
    return UserDto.of(users.saveAndFlush(user));
  }

  @PatchMapping("/{id}")
  @Transactional
  public UserDto update(
      @AuthenticationPrincipal AuthUser me, @PathVariable String id, @RequestBody UpdateUserRequest body) {
    me.requireAdmin();
    UUID userId = Ids.pathId(id, "User");
    boolean demotingSelf = body.role() != null && body.role() != Role.ADMIN;
    if (userId.equals(me.id()) && (Boolean.FALSE.equals(body.active()) || demotingSelf)) {
      throw ApiException.badRequest("You can't deactivate yourself or remove your own admin role.");
    }
    User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    if (body.name() != null) user.setName(requireName(body.name()));
    if (body.role() != null) user.setRole(body.role());
    if (body.active() != null) user.setActive(body.active());
    if (body.password() != null) {
      Passwords.validate(body.password(), "password");
      user.setPasswordHash(encoder.encode(body.password()));
    }
    user.setUpdatedAt(clock.instant());
    return UserDto.of(users.saveAndFlush(user));
  }

  private static String requireName(String raw) {
    String name = raw == null ? "" : raw.trim();
    if (name.isEmpty()) throw ApiException.validation("name", "is required");
    if (name.length() > 120) throw ApiException.validation("name", "must be at most 120 characters");
    return name;
  }
}
