package com.leadtracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadtracker.user.Role;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LeadsApiTest extends ApiTestBase {

  @Test
  void createsALeadWithDefaultsAndACreatedActivity() throws Exception {
    Session s = signIn(Role.REP);
    JsonNode lead =
        createLead(s, Map.of("name", "Ananya Rao", "company", "Brightleaf", "valueInr", 450000, "ownerId", s.id().toString()));
    assertThat(lead.get("name").asText()).isEqualTo("Ananya Rao");
    assertThat(lead.get("stage").asText()).isEqualTo("NEW");
    assertThat(lead.get("source").asText()).isEqualTo("WEBSITE");
    assertThat(lead.get("valueInr").asDouble()).isEqualTo(450000.0);
    assertThat(lead.get("owner").get("id").asText()).isEqualTo(s.id().toString());
    assertThat(lead.get("createdBy").get("name").asText()).isEqualTo(s.name());
    assertThat(lead.get("nextFollowUp").isNull()).isTrue();

    getAs(s, "/api/leads/" + lead.get("id").asText() + "/activities")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].type").value("CREATED"))
        .andExpect(jsonPath("$.items[0].body").value("Lead added in New"));
  }

  @Test
  void reportsEveryInvalidField() throws Exception {
    Session s = signIn(Role.REP);
    JsonNode err =
        body(
            postAs(s, "/api/leads", Map.of("name", "", "email", "not-an-email", "nextFollowUp", "15/10/2026", "stage", "DONE"))
                .andExpect(status().isBadRequest()));
    assertThat(err.at("/error/code").asText()).isEqualTo("validation_error");
    List<String> fields = new ArrayList<>();
    err.at("/error/details").forEach(d -> fields.add(d.get("field").asText()));
    assertThat(fields).contains("name", "email", "nextFollowUp", "stage");
  }

  @Test
  void logsAStageChangeAndSetsWonAt() throws Exception {
    Session s = signIn(Role.REP);
    String id = createLead(s, Map.of("name", "Rohit", "stage", "PROPOSAL")).get("id").asText();
    patchAs(s, "/api/leads/" + id, Map.of("stage", "WON"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value("WON"))
        .andExpect(jsonPath("$.wonAt").isNotEmpty());
    getAs(s, "/api/leads/" + id + "/activities")
        .andExpect(jsonPath("$.items[0].type").value("STAGE_CHANGE"))
        .andExpect(jsonPath("$.items[0].body").value("Moved from Proposal sent to Won"))
        .andExpect(jsonPath("$.items[0].user.id").value(s.id().toString()));
  }

  @Test
  void doesNotLogAStageChangeWhenTheStageIsUnchanged() throws Exception {
    Session s = signIn(Role.REP);
    String id = createLead(s, Map.of("name", "Same")).get("id").asText();
    patchAs(s, "/api/leads/" + id, Map.of("stage", "NEW", "notes", "hi"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").value("hi"));
    getAs(s, "/api/leads/" + id + "/activities").andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void patchLeavesAbsentFieldsAloneAndClearsNullOnes() throws Exception {
    Session s = signIn(Role.REP);
    String id =
        createLead(s, Map.of("name", "Meera", "company", "Northstar", "nextFollowUp", today().toString()))
            .get("id")
            .asText();
    Map<String, Object> patch = new HashMap<>();
    patch.put("nextFollowUp", null);
    patchAs(s, "/api/leads/" + id, patch)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.company").value("Northstar"))
        .andExpect(jsonPath("$.nextFollowUp").isEmpty());
  }

  @Test
  void filtersBySearchOwnerStageAndFollowUpWindow() throws Exception {
    Session s = signIn(Role.REP);
    LocalDate t = today();
    createLead(s, Map.of("name", "Overdue Co", "nextFollowUp", t.minusDays(3).toString(), "ownerId", s.id().toString()));
    createLead(s, Map.of("name", "Today Co", "nextFollowUp", t.toString()));
    createLead(s, Map.of("name", "Week Co", "nextFollowUp", t.plusDays(4).toString()));
    createLead(s, Map.of("name", "Closed Co", "nextFollowUp", t.minusDays(3).toString(), "stage", "WON"));

    assertThat(names(s, "followUp=overdue")).containsExactly("Overdue Co");
    assertThat(names(s, "followUp=today")).containsExactly("Today Co");
    assertThat(names(s, "followUp=week")).containsExactly("Today Co", "Week Co");
    assertThat(names(s, "owner=me")).containsExactly("Overdue Co");
    assertThat(names(s, "owner=none")).containsExactly("Closed Co", "Today Co", "Week Co");
    assertThat(names(s, "q=week")).containsExactly("Week Co");
    assertThat(names(s, "stage=won,lost")).containsExactly("Closed Co");
    assertThat(names(s, "open=false")).containsExactly("Closed Co");

    getAs(s, "/api/leads?sort=bogus").andExpect(status().isBadRequest());
    getAs(s, "/api/leads?stage=DONE").andExpect(status().isBadRequest());
  }

  @Test
  void sortsAndPages() throws Exception {
    Session s = signIn(Role.REP);
    createLead(s, Map.of("name", "B", "valueInr", 300));
    createLead(s, Map.of("name", "A", "valueInr", 100));
    createLead(s, Map.of("name", "C", "valueInr", 200));
    JsonNode page1 = body(getAs(s, "/api/leads?sort=valueInr&order=desc&pageSize=2&page=1"));
    assertThat(page1.get("total").asInt()).isEqualTo(3);
    assertThat(page1.get("items").get(0).get("name").asText()).isEqualTo("B");
    assertThat(page1.get("items").get(1).get("name").asText()).isEqualTo("C");
    JsonNode page2 = body(getAs(s, "/api/leads?sort=valueInr&order=desc&pageSize=2&page=2"));
    assertThat(page2.get("items").get(0).get("name").asText()).isEqualTo("A");
    assertThat(names(s, "sort=name&order=asc")).containsExactly("A", "B", "C");
  }

  @Test
  void logsAnActivityAndMovesTheFollowUpInOneStep() throws Exception {
    Session s = signIn(Role.REP);
    String id = createLead(s, Map.of("name", "Meera")).get("id").asText();
    String next = today().plusDays(3).toString();
    postAs(s, "/api/leads/" + id + "/activities", Map.of("type", "CALL", "body", "Spoke to CFO", "nextFollowUp", next))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.activity.type").value("CALL"))
        .andExpect(jsonPath("$.activity.body").value("Spoke to CFO"))
        .andExpect(jsonPath("$.lead.nextFollowUp").value(next))
        .andExpect(jsonPath("$.lead.lastContactAt").isNotEmpty());
    postAs(s, "/api/leads/" + id + "/activities", Map.of("type", "STAGE_CHANGE", "body", "x"))
        .andExpect(status().isBadRequest());
    postAs(s, "/api/leads/" + id + "/activities", Map.of("type", "NOTE", "body", "  "))
        .andExpect(status().isBadRequest());
  }

  @Test
  void answers404ForUnknownLeads() throws Exception {
    Session s = signIn(Role.REP);
    getAs(s, "/api/leads/00000000-0000-0000-0000-000000000000").andExpect(status().isNotFound());
    getAs(s, "/api/leads/not-a-uuid").andExpect(status().isNotFound());
    patchAs(s, "/api/leads/00000000-0000-0000-0000-000000000000", Map.of("name", "X")).andExpect(status().isNotFound());
  }

  private List<String> names(Session s, String query) throws Exception {
    JsonNode page = body(getAs(s, "/api/leads?" + query).andExpect(status().isOk()));
    List<String> names = new ArrayList<>();
    page.get("items").forEach(l -> names.add(l.get("name").asText()));
    names.sort(null);
    if (query.startsWith("sort=")) {
      names.clear();
      page.get("items").forEach(l -> names.add(l.get("name").asText()));
    }
    return names;
  }
}
