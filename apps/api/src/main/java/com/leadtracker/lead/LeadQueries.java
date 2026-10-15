package com.leadtracker.lead;

import com.leadtracker.activity.ActivityDto;
import com.leadtracker.activity.ActivityType;
import com.leadtracker.common.Dates;
import com.leadtracker.config.AppProperties;
import com.leadtracker.lead.LeadDto.UserRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read queries for leads and activities, written in SQL so a list is one query with owner and creator names
 * joined in, and so sorting can use NULLS LAST and pipeline order.
 */
@Repository
public class LeadQueries {

  private static final String SELECT =
      """
      SELECT l.id, l.name, l.company, l.email, l.phone, l.source, l.stage, l.value_inr, l.next_follow_up,
             l.notes, l.lost_reason, l.stage_changed_at, l.won_at, l.last_contact_at, l.created_at, l.updated_at,
             o.id AS owner_ref_id, o.name AS owner_ref_name, o.email AS owner_ref_email,
             c.id AS creator_ref_id, c.name AS creator_ref_name, c.email AS creator_ref_email
      FROM leads l
      LEFT JOIN users o ON o.id = l.owner_id
      LEFT JOIN users c ON c.id = l.created_by_id
      """;

  private static final String STAGE_ORDER =
      "CASE l.stage WHEN 'NEW' THEN 0 WHEN 'CONTACTED' THEN 1 WHEN 'QUALIFIED' THEN 2 WHEN 'PROPOSAL' THEN 3 "
          + "WHEN 'NEGOTIATION' THEN 4 WHEN 'WON' THEN 5 ELSE 6 END";

  private static final Map<String, String> SORT_SQL =
      Map.of(
          "name", "lower(l.name)",
          "company", "lower(l.company)",
          "valueInr", "l.value_inr",
          "nextFollowUp", "l.next_follow_up",
          "updatedAt", "l.updated_at",
          "createdAt", "l.created_at",
          "stage", STAGE_ORDER);

  private static final List<String> OPEN_STAGE_NAMES = Stage.OPEN.stream().map(Enum::name).toList();

  private final NamedParameterJdbcTemplate jdbc;
  private final AppProperties props;
  private final Clock clock;

  public LeadQueries(NamedParameterJdbcTemplate jdbc, AppProperties props, Clock clock) {
    this.jdbc = jdbc;
    this.props = props;
    this.clock = clock;
  }

  public Optional<LeadDto> find(UUID id) {
    List<LeadDto> rows = jdbc.query(SELECT + " WHERE l.id = :id", Map.of("id", id), LeadQueries::mapLead);
    return rows.stream().findFirst();
  }

  public List<LeadDto> list(LeadListQuery query, UUID viewerId) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    String where = where(query, viewerId, params);
    String dir = query.ascending() ? "ASC" : "DESC";
    String nulls = query.sort().equals("nextFollowUp") || query.sort().equals("company") ? " NULLS LAST" : "";
    params.addValue("limit", query.pageSize());
    params.addValue("offset", (long) (query.page() - 1) * query.pageSize());
    String sql =
        SELECT + where + " ORDER BY " + SORT_SQL.get(query.sort()) + " " + dir + nulls + ", l.id ASC LIMIT :limit OFFSET :offset";
    return jdbc.query(sql, params, LeadQueries::mapLead);
  }

  public long count(LeadListQuery query, UUID viewerId) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    String sql = "SELECT count(*) FROM leads l" + where(query, viewerId, params);
    Long n = jdbc.queryForObject(sql, params, Long.class);
    return n == null ? 0 : n;
  }

  public List<ActivityDto> activities(UUID leadId) {
    return jdbc.query(
        ACTIVITY_SELECT + " WHERE a.lead_id = :leadId ORDER BY a.created_at DESC, a.id DESC LIMIT 500",
        Map.of("leadId", leadId),
        LeadQueries::mapActivity);
  }

  public Optional<ActivityDto> activity(UUID id) {
    return jdbc.query(ACTIVITY_SELECT + " WHERE a.id = :id", Map.of("id", id), LeadQueries::mapActivity).stream()
        .findFirst();
  }

  private static final String ACTIVITY_SELECT =
      """
      SELECT a.id, a.lead_id, a.type, a.body, a.created_at,
             u.id AS author_ref_id, u.name AS author_ref_name, u.email AS author_ref_email
      FROM activities a
      LEFT JOIN users u ON u.id = a.user_id
      """;

  /* ---------- filters ---------- */

  private String where(LeadListQuery f, UUID viewerId, MapSqlParameterSource params) {
    List<String> conds = new ArrayList<>();
    if (f.q() != null) {
      String like = "%" + f.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
      params.addValue("q", like);
      conds.add(
          "(l.name ILIKE :q OR l.company ILIKE :q OR l.email ILIKE :q OR l.phone ILIKE :q OR l.notes ILIKE :q)");
    }
    if (!f.stages().isEmpty()) {
      params.addValue("stages", f.stages().stream().map(Enum::name).toList());
      conds.add("l.stage IN (:stages)");
    }
    if (f.source() != null) {
      params.addValue("source", f.source().name());
      conds.add("l.source = :source");
    }
    if ("me".equals(f.owner())) {
      params.addValue("viewer", viewerId);
      conds.add("l.owner_id = :viewer");
    } else if ("none".equals(f.owner())) {
      conds.add("l.owner_id IS NULL");
    } else if (f.owner() != null) {
      params.addValue("ownerId", UUID.fromString(f.owner()));
      conds.add("l.owner_id = :ownerId");
    }
    if (Boolean.TRUE.equals(f.open()) || f.followUp() != null) {
      params.addValue("openStages", OPEN_STAGE_NAMES);
      conds.add("l.stage IN (:openStages)");
    } else if (Boolean.FALSE.equals(f.open())) {
      params.addValue("openStages", OPEN_STAGE_NAMES);
      conds.add("l.stage NOT IN (:openStages)");
    }
    if (f.followUp() != null) {
      LocalDate today = Dates.today(clock, props.zone());
      params.addValue("today", today);
      switch (f.followUp()) {
        case "overdue" -> conds.add("l.next_follow_up < :today");
        case "today" -> conds.add("l.next_follow_up = :today");
        case "week" -> {
          params.addValue("weekEnd", today.plusDays(7));
          conds.add("l.next_follow_up BETWEEN :today AND :weekEnd");
        }
        case "none" -> conds.add("l.next_follow_up IS NULL");
        default -> {}
      }
    }
    return conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
  }

  /* ---------- row mapping ---------- */

  static LeadDto mapLead(ResultSet rs, int rowNum) throws SQLException {
    return new LeadDto(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("company"),
        rs.getString("email"),
        rs.getString("phone"),
        LeadSource.valueOf(rs.getString("source")),
        Stage.valueOf(rs.getString("stage")),
        rs.getBigDecimal("value_inr").doubleValue(),
        rs.getObject("next_follow_up", LocalDate.class),
        rs.getString("notes"),
        rs.getString("lost_reason"),
        ref(rs, "owner_ref"),
        ref(rs, "creator_ref"),
        instant(rs, "stage_changed_at"),
        instant(rs, "won_at"),
        instant(rs, "last_contact_at"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  static ActivityDto mapActivity(ResultSet rs, int rowNum) throws SQLException {
    return new ActivityDto(
        rs.getObject("id", UUID.class),
        rs.getObject("lead_id", UUID.class),
        ActivityType.valueOf(rs.getString("type")),
        rs.getString("body"),
        ref(rs, "author_ref"),
        instant(rs, "created_at"));
  }

  private static UserRef ref(ResultSet rs, String prefix) throws SQLException {
    UUID id = rs.getObject(prefix + "_id", UUID.class);
    return id == null ? null : new UserRef(id, rs.getString(prefix + "_name"), rs.getString(prefix + "_email"));
  }

  /** Timestamps are returned to millisecond precision, which every browser parses. */
  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
    return t == null ? null : t.toInstant().truncatedTo(ChronoUnit.MILLIS);
  }
}
