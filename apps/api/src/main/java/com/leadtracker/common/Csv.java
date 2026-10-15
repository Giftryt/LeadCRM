package com.leadtracker.common;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 CSV reader and writer (quoted fields, escaped quotes, CRLF or LF line endings). */
public final class Csv {

  private Csv() {}

  /** Parses CSV text into rows of trimmed cells, dropping blank rows and a leading byte-order mark. */
  public static List<List<String>> parse(String text) {
    if (text.startsWith("﻿")) text = text.substring(1);
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            cell.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          cell.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        row.add(cell.toString().trim());
        cell.setLength(0);
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
        row.add(cell.toString().trim());
        cell.setLength(0);
        addIfNotBlank(rows, row);
        row = new ArrayList<>();
      } else {
        cell.append(c);
      }
    }
    if (quoted) throw new IllegalArgumentException("A quoted field is not closed");
    if (cell.length() > 0 || !row.isEmpty()) {
      row.add(cell.toString().trim());
      addIfNotBlank(rows, row);
    }
    return rows;
  }

  private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
    for (String v : row) {
      if (!v.isEmpty()) {
        rows.add(row);
        return;
      }
    }
  }

  /** Quotes a value when it contains a comma, quote or line break. */
  public static String escape(Object value) {
    String s = value == null ? "" : String.valueOf(value);
    if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }

  public static String line(List<?> values) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append(escape(values.get(i)));
    }
    return sb.append('\n').toString();
  }
}
