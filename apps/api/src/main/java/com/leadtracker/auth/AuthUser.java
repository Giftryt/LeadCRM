package com.leadtracker.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.leadtracker.common.ApiException;
import com.leadtracker.user.Role;
import com.leadtracker.user.User;
import java.util.UUID;

/** The signed-in user, available in controllers via {@code @AuthenticationPrincipal AuthUser user}. */
public record AuthUser(UUID id, String email, String name, Role role) {

  public static AuthUser of(User u) {
    return new AuthUser(u.getId(), u.getEmail(), u.getName(), u.getRole());
  }

  @JsonIgnore
  public boolean isAdmin() {
    return role == Role.ADMIN;
  }

  /** Throws 403 for read-only users. */
  public void requireWriter() {
    if (!role.canWrite()) throw ApiException.forbidden();
  }

  /** Throws 403 unless the user is an admin. */
  public void requireAdmin() {
    if (!isAdmin()) throw ApiException.forbidden();
  }
}
