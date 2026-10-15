package com.leadtracker.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadtracker.activity.ActivityDto;
import com.leadtracker.activity.ActivityType;
import com.leadtracker.auth.AuthUser;
import com.leadtracker.common.ApiException;
import com.leadtracker.common.Csv;
import com.leadtracker.common.Dates;
import com.leadtracker.common.FieldError;
import com.leadtracker.common.Ids;
import com.leadtracker.common.Items;
import com.leadtracker.common.PageResponse;
import com.leadtracker.config.AppProperties;
import com.leadtracker.lead.LeadImportService.ImportResult;
import com.leadtracker.lead.LeadService.LoggedActivity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

  private final LeadService service;
  private final LeadImportService importer;
  private final LeadQueries queries;
  private final AppProperties props;
  private final Clock clock;

  public LeadController(
      LeadService service, LeadImportService importer, LeadQueries queries, AppProperties props, Clock clock) {
    this.service = service;
    this.importer = importer;
    this.queries = queries;
    this.props = props;
    this.clock = clock;
  }

  @GetMapping
  public PageResponse<LeadDto> list(@AuthenticationPrincipal AuthUser me, @RequestParam Map<String, String> params) {
    LeadListQuery q = LeadListQuery.from(params);
    return new PageResponse<>(queries.list(q, me.id()), queries.count(q, me.id()), q.page(), q.pageSize());
  }

  @GetMapping("/export.csv")
  public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser me, @RequestParam Map<String, String> params) {
    Map<String, String> p = new HashMap<>(params);
    p.put("pageSize", "1000");
    p.remove("page");
    LeadListQuery base = LeadListQuery.from(p);
    StringBuilder csv = new StringBuilder("﻿"); // BOM so Excel opens UTF-8 (₹, names) correctly
    csv.append(
        Csv.line(
            List.of(
                "Name", "Company", "Email", "Phone", "Stage", "Value (INR)", "Owner", "Source", "Next follow-up",
                "Notes", "Reason lost", "Created")));
    // Page through in chunks of 1000, up to 50,000 rows.
    for (int page = 1; page <= 50; page++) {
      LeadListQuery q =
          new LeadListQuery(
              base.q(), base.stages(), base.source(), base.owner(), base.followUp(), base.open(), "updatedAt",
              false, page, 1000);
      List<LeadDto> rows = queries.list(q, me.id());
      for (LeadDto l : rows) {
        List<Object> cells = new ArrayList<>();
        cells.add(l.name());
        cells.add(l.company());
        cells.add(l.email());
        cells.add(l.phone());
        cells.add(l.stage().label());
        cells.add(Math.round(l.valueInr()));
        cells.add(l.owner() == null ? "" : l.owner().name());
        cells.add(l.source().label());
        cells.add(l.nextFollowUp());
        cells.add(l.notes());
        cells.add(l.lostReason());
        cells.add(l.createdAt() == null ? "" : l.createdAt().atOffset(ZoneOffset.UTC).toLocalDate());
        csv.append(Csv.line(cells));
      }
      if (rows.size() < 1000) break;
    }
    String filename = "leads-" + Dates.today(clock, props.zone()) + ".csv";
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LeadDto create(@AuthenticationPrincipal AuthUser me, @RequestBody JsonNode body) {
    me.requireWriter();
    return service.create(LeadInput.parse(body, true), me);
  }

  @GetMapping("/{id}")
  public LeadDto get(@PathVariable String id) {
    return queries.find(Ids.pathId(id, "Lead")).orElseThrow(() -> ApiException.notFound("Lead"));
  }

  @PatchMapping("/{id}")
  public LeadDto update(@AuthenticationPrincipal AuthUser me, @PathVariable String id, @RequestBody JsonNode body) {
    me.requireWriter();
    UUID leadId = Ids.pathId(id, "Lead");
    return service.update(leadId, LeadInput.parse(body, false), me);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
    me.requireWriter();
    service.delete(Ids.pathId(id, "Lead"), me);
    return ResponseEntity.noContent().build();
  }

  /* ---------- activity ---------- */

  @GetMapping("/{id}/activities")
  public Items<ActivityDto> activities(@PathVariable String id) {
    UUID leadId = Ids.pathId(id, "Lead");
    if (queries.find(leadId).isEmpty()) throw ApiException.notFound("Lead");
    return new Items<>(queries.activities(leadId));
  }

  /**
   * Body: {@code {"type": "CALL"|"EMAIL"|"MEETING"|"NOTE", "body": "...", "nextFollowUp": "YYYY-MM-DD"|null}}.
   * {@code nextFollowUp} is optional; when given it sets (or, with null, clears) the lead's next follow-up.
   */
  @PostMapping("/{id}/activities")
  @ResponseStatus(HttpStatus.CREATED)
  public LoggedActivity logActivity(
      @AuthenticationPrincipal AuthUser me, @PathVariable String id, @RequestBody JsonNode body) {
    me.requireWriter();
    UUID leadId = Ids.pathId(id, "Lead");
    if (body == null || !body.isObject()) throw ApiException.badRequest("Send the activity as a JSON object.");

    List<FieldError> errors = new ArrayList<>();
    ActivityType type = null;
    JsonNode t = body.get("type");
    if (t != null && t.isTextual()) {
      try {
        type = ActivityType.valueOf(t.asText());
      } catch (IllegalArgumentException e) {
        type = null;
      }
    }
    if (type == null || !ActivityType.LOGGABLE.contains(type)) {
      errors.add(new FieldError("type", "must be one of CALL, EMAIL, MEETING, NOTE"));
    }
    JsonNode b = body.get("body");
    String text = b != null && b.isTextual() ? b.asText().trim() : "";
    if (text.isEmpty()) errors.add(new FieldError("body", "is required"));
    else if (text.length() > 10_000) errors.add(new FieldError("body", "must be at most 10000 characters"));

    boolean followUpGiven = body.has("nextFollowUp");
    Optional<LocalDate> next = Optional.empty();
    if (followUpGiven) {
      JsonNode n = body.get("nextFollowUp");
      if (!n.isNull() && !(n.isTextual() && n.asText().isEmpty())) {
        LocalDate d = n.isTextual() ? Dates.parseIso(n.asText()) : null;
        if (d == null) errors.add(new FieldError("nextFollowUp", "must be a date in YYYY-MM-DD format"));
        next = Optional.ofNullable(d);
      }
    }
    if (!errors.isEmpty()) throw ApiException.validation(errors);
    return service.logActivity(leadId, type, text, next, followUpGiven, me);
  }

  /* ---------- CSV import ---------- */

  @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ImportResult importCsv(
      @AuthenticationPrincipal AuthUser me, @RequestParam(value = "file", required = false) MultipartFile file)
      throws IOException {
    me.requireWriter();
    if (file == null || file.isEmpty()) throw ApiException.badRequest("Attach a CSV file in the 'file' field.");
    return importer.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8), me);
  }
}
