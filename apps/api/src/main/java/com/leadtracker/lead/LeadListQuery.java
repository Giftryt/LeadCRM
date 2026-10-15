package com.leadtracker.lead;

import com.leadtracker.common.ApiException;
import com.leadtracker.common.Ids;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Filters, sort and paging for {@code GET /api/leads} and the CSV export, parsed from query parameters.
 *
 * @param owner "me", "none", a user id, or null for everyone
 * @param followUp "overdue", "today", "week", "none" or null
 * @param open true (not won/lost), false (won/lost) or null
 */
public record LeadListQuery(
    String q,
    List<Stage> stages,
    LeadSource source,
    String owner,
    String followUp,
    Boolean open,
    String sort,
    boolean ascending,
    int page,
    int pageSize) {

  public static final Set<String> SORTS =
      Set.of("name", "company", "valueInr", "nextFollowUp", "updatedAt", "createdAt", "stage");
  private static final Set<String> FOLLOW_UPS = Set.of("overdue", "today", "week", "none");

  public static LeadListQuery from(Map<String, String> p) {
    String q = blankToNull(p.get("q"));
    if (q != null && q.length() > 200) throw ApiException.validation("q", "must be at most 200 characters");

    List<Stage> stages = new ArrayList<>();
    String stageParam = blankToNull(p.get("stage"));
    if (stageParam != null) {
      for (String s : stageParam.split(",")) {
        try {
          stages.add(Stage.valueOf(s.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
          throw ApiException.validation("stage", "has an unknown stage \"" + s.trim() + "\"");
        }
      }
    }

    LeadSource source = null;
    String sourceParam = blankToNull(p.get("source"));
    if (sourceParam != null) {
      try {
        source = LeadSource.valueOf(sourceParam);
      } catch (IllegalArgumentException e) {
        throw ApiException.validation("source", "is not a known source");
      }
    }

    String owner = blankToNull(p.get("owner"));
    if (owner != null && !owner.equals("me") && !owner.equals("none") && Ids.parse(owner) == null) {
      throw ApiException.validation("owner", "must be me, none or a user id");
    }

    String followUp = blankToNull(p.get("followUp"));
    if (followUp != null && !FOLLOW_UPS.contains(followUp)) {
      throw ApiException.validation("followUp", "must be overdue, today, week or none");
    }

    Boolean open = null;
    String openParam = blankToNull(p.get("open"));
    if (openParam != null) {
      if (!openParam.equals("true") && !openParam.equals("false")) {
        throw ApiException.validation("open", "must be true or false");
      }
      open = Boolean.valueOf(openParam);
    }

    String sort = p.getOrDefault("sort", "updatedAt");
    if (!SORTS.contains(sort)) throw ApiException.validation("sort", "must be one of " + String.join(", ", SORTS));
    String order = p.getOrDefault("order", "desc");
    if (!order.equals("asc") && !order.equals("desc")) throw ApiException.validation("order", "must be asc or desc");

    int page = intParam(p, "page", 1, 1, Integer.MAX_VALUE);
    int pageSize = intParam(p, "pageSize", 100, 1, 1000);
    return new LeadListQuery(q, stages, source, owner, followUp, open, sort, order.equals("asc"), page, pageSize);
  }

  private static int intParam(Map<String, String> p, String name, int def, int min, int max) {
    String v = blankToNull(p.get(name));
    if (v == null) return def;
    try {
      int n = Integer.parseInt(v);
      if (n < min || n > max) throw new NumberFormatException();
      return n;
    } catch (NumberFormatException e) {
      throw ApiException.validation(name, "must be a whole number from " + min + " to " + max);
    }
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
