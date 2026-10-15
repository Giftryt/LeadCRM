package com.leadtracker.common;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, int page, int pageSize) {}
