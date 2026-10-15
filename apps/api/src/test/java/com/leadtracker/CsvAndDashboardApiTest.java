package com.leadtracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadtracker.user.Role;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class CsvAndDashboardApiTest extends ApiTestBase {

  @Test
  void importsRowsMapsHeadersAndReportsBadRows() throws Exception {
    Session admin = signIn(Role.ADMIN, "Asha Admin");
    String csv =
        String.join(
            "\n",
            "Name,Company,Email,Stage,Value,Next follow-up,Source,Owner",
            "Ravi Kumar,Acme,ravi@acme.in,Proposal sent,\"₹2,50,000\",20/11/2026,LinkedIn,Asha Admin",
            ",Only Company Pvt Ltd,,,,,,",
            "Bad Email,X,not-an-email,,,,,",
            "Bad Date,Y,,,,31/02/2026,,");
    MockMultipartFile file =
        new MockMultipartFile("file", "leads.csv", "text/csv", ("﻿" + csv).getBytes(StandardCharsets.UTF_8));
    JsonNode result =
        body(mvc.perform(auth(multipart("/api/leads/import").file(file), admin)).andExpect(status().isCreated()));
    assertThat(result.get("imported").asInt()).isEqualTo(2);
    assertThat(result.get("skipped").asInt()).isEqualTo(2);
    List<Integer> rows = new ArrayList<>();
    result.get("errors").forEach(e -> rows.add(e.get("row").asInt()));
    assertThat(rows).containsExactly(4, 5);

    JsonNode page = body(getAs(admin, "/api/leads?sort=name&order=asc"));
    JsonNode ravi = null;
    for (JsonNode l : page.get("items")) if (l.get("name").asText().equals("Ravi Kumar")) ravi = l;
    assertThat(ravi).isNotNull();
    assertThat(ravi.get("stage").asText()).isEqualTo("PROPOSAL");
    assertThat(ravi.get("valueInr").asDouble()).isEqualTo(250000.0);
    assertThat(ravi.get("nextFollowUp").asText()).isEqualTo("2026-11-20");
    assertThat(ravi.get("source").asText()).isEqualTo("LINKEDIN");
    assertThat(ravi.get("owner").get("name").asText()).isEqualTo("Asha Admin");
    assertThat(page.toString()).contains("Only Company Pvt Ltd");
  }

  @Test
  void rejectsAFileWithoutANameColumn() throws Exception {
    Session rep = signIn(Role.REP);
    MockMultipartFile file =
        new MockMultipartFile("file", "x.csv", "text/csv", "Foo,Bar\n1,2\n".getBytes(StandardCharsets.UTF_8));
    mvc.perform(auth(multipart("/api/leads/import").file(file), rep))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Name or Company")));
  }

  @Test
  void exportsCsvWithAHeaderRow() throws Exception {
    Session s = signIn(Role.REP);
    createLead(s, Map.of("name", "Export, Me", "valueInr", 1000));
    MvcResult r = getAs(s, "/api/leads/export.csv").andExpect(status().isOk()).andReturn();
    assertThat(r.getResponse().getContentType()).contains("text/csv");
    assertThat(r.getResponse().getHeader("Content-Disposition")).contains("attachment").contains(".csv");
    String text = r.getResponse().getContentAsString(StandardCharsets.UTF_8).replace("﻿", "");
    String[] lines = text.trim().split("\n");
    assertThat(lines[0]).startsWith("Name,Company,Email");
    assertThat(lines[1]).startsWith("\"Export, Me\"");
  }

  @Test
  void summarisesPipelineForecastWinsAndFollowUps() throws Exception {
    Session s = signIn(Role.REP);
    LocalDate t = today();
    createLead(s, Map.of("name", "A", "stage", "PROPOSAL", "valueInr", 100000, "nextFollowUp", t.minusDays(1).toString()));
    createLead(s, Map.of("name", "B", "stage", "NEW", "valueInr", 50000, "nextFollowUp", t.toString()));
    String c = createLead(s, Map.of("name", "C", "stage", "NEGOTIATION", "valueInr", 200000)).get("id").asText();
    patchAs(s, "/api/leads/" + c, Map.of("stage", "WON")).andExpect(status().isOk());
    createLead(s, Map.of("name", "D", "stage", "LOST", "valueInr", 10000));

    getAs(s, "/api/dashboard/summary")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openPipeline.count").value(2))
        .andExpect(jsonPath("$.openPipeline.valueInr").value(150000.0))
        .andExpect(jsonPath("$.weightedForecastInr").value(65000)) // 1,00,000 × 0.6 + 50,000 × 0.1
        .andExpect(jsonPath("$.wonThisMonth.count").value(1))
        .andExpect(jsonPath("$.wonThisMonth.valueInr").value(200000.0))
        .andExpect(jsonPath("$.winRate").value(0.5))
        .andExpect(jsonPath("$.followUps.overdue").value(1))
        .andExpect(jsonPath("$.followUps.today").value(1))
        .andExpect(jsonPath("$.stages.length()").value(7))
        .andExpect(jsonPath("$.stages[0].stage").value("NEW"));

    getAs(s, "/api/dashboard/summary?owner=me").andExpect(jsonPath("$.openPipeline.count").value(0));
  }
}
