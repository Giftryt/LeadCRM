package com.leadtracker.auth;

import com.leadtracker.common.ApiException;

/** Password rules shared by sign-up, admin resets and password changes. */
public final class Passwords {

  private Passwords() {}

  /** Throws a validation error for {@code field} if the password is too weak. */
  public static void validate(String password, String field) {
    if (password == null || password.length() < 10) {
      throw ApiException.validation(field, "must be at least 10 characters");
    }
    if (password.length() > 200) {
      throw ApiException.validation(field, "must be at most 200 characters");
    }
    boolean letter = password.chars().anyMatch(Character::isLetter);
    boolean digit = password.chars().anyMatch(Character::isDigit);
    if (!letter || !digit) {
      throw ApiException.validation(field, "must include at least one letter and one number");
    }
  }
}
