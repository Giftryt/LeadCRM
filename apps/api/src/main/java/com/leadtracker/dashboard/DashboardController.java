package com.leadtracker.dashboard;

import com.leadtracker.auth.AuthUser;
import com.leadtracker.common.Dates;
import com.leadtracker.config.AppProperties;
import com.leadtracker.lead.Stage;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Headline numbers for the summary strip and per-stage totals. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  public record CountValue(long count, double valueInr) {}

  public record FollowUps(long overdue, long today) {}

  public record StageTotal(Stage stage, long count, double valueInr) {}

  public record Summary(
      CountValue openPipeline,
      long weightedForecastInr,
      CountValue wonThisMonth,
      Double winRate,
      FollowUps followUps,
      List<StageTotal> stages) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final AppProperties props;
  private final Clock clock;

  public DashboardController(NamedParameterJdbcTemplate jdbc, AppProperties props, Clock clock) {
    this.jdbc = jdbc;
    this.props = props;
    this.clock = clock;
  }

  /** {@code GET /api/dashboard/summary?owner=me|none} */
  @GetMapping("/summary")
  public Summary summary(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String owner) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    String ownerCond = "TRUE";
    if ("me".equals(owner)) {
      ownerCond = "owner_id = :viewer";
      p.addValue("viewer", me.id());
    } else if ("none".equals(owner)) {
      ownerCond = "owner_id IS NULL";
    }
    LocalDate today = Dates.today(clock, props.zone());
    p.addValue("today", today);
    p.addValue("monthStart", Timestamp.from(Dates.startOfMonth(clock, props.zone())));
    p.addValue("openStages", Stage.OPEN.stream().map(Enum::name).toList());

    Map<Stage, StageTotal> byStage = new HashMap<>();
    jdbc.query(
        "SELECT stage, count(*) AS n, coalesce(sum(value_inr), 0) AS v FROM leads WHERE " + ownerCond + " GROUP BY stage",
        p,
        rs -> {
          Stage s = Stage.valueOf(rs.getString("stage"));
          byStage.put(s, new StageTotal(s, rs.getLong("n"), rs.getBigDecimal("v").doubleValue()));
        });

    Map<String, Object> won =
        jdbc.queryForMap(
            "SELECT count(*) AS n, coalesce(sum(value_inr), 0) AS v FROM leads WHERE "
                + ownerCond
                + " AND stage = 'WON' AND won_at >= :monthStart",
            p);
    Long overdue =
        jdbc.queryForObject(
            "SELECT count(*) FROM leads WHERE " + ownerCond + " AND stage IN (:openStages) AND next_follow_up < :today",
            p,
            Long.class);
    Long dueToday =
        jdbc.queryForObject(
            "SELECT count(*) FROM leads WHERE " + ownerCond + " AND stage IN (:openStages) AND next_follow_up = :today",
            p,
            Long.class);

    List<StageTotal> stages = new ArrayList<>();
    long openCount = 0;
    double openValue = 0;
    double weighted = 0;
    for (Stage s : Stage.values()) {
      StageTotal t = byStage.getOrDefault(s, new StageTotal(s, 0, 0));
      stages.add(t);
      if (s.isOpen()) {
        openCount += t.count();
        openValue += t.valueInr();
        weighted += t.valueInr() * s.probability();
      }
    }
    long wonCount = byStage.getOrDefault(Stage.WON, new StageTotal(Stage.WON, 0, 0)).count();
    long lostCount = byStage.getOrDefault(Stage.LOST, new StageTotal(Stage.LOST, 0, 0)).count();
    long closed = wonCount + lostCount;

    return new Summary(
        new CountValue(openCount, openValue),
        Math.round(weighted),
        new CountValue(((Number) won.get("n")).longValue(), ((BigDecimal) won.get("v")).doubleValue()),
        closed == 0 ? null : (double) wonCount / closed,
        new FollowUps(overdue == null ? 0 : overdue, dueToday == null ? 0 : dueToday),
        stages);
  }
}
