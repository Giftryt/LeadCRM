package com.leadtracker.common;

import java.util.List;

/** {@code {"items": [...]}} wrapper for unpaged lists. */
public record Items<T>(List<T> items) {}
