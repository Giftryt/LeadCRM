package com.leadtracker.user;

public enum Role {
  /** Everything, including managing users and deleting any lead. */
  ADMIN,
  /** Adds and works leads. */
  REP,
  /** Read-only. */
  VIEWER;

  public boolean canWrite() {
    return this != VIEWER;
  }
}
