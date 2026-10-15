package com.leadtracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leadtracker.user.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PermissionsApiTest extends ApiTestBase {

  @Test
  void viewersCanReadButNotWrite() throws Exception {
    Session rep = signIn(Role.REP);
    Session viewer = signIn(Role.VIEWER);
    String id = createLead(rep, Map.of("name", "Kabir")).get("id").asText();

    getAs(viewer, "/api/leads/" + id).andExpect(status().isOk());
    getAs(viewer, "/api/leads").andExpect(status().isOk());
    postAs(viewer, "/api/leads", Map.of("name", "X")).andExpect(status().isForbidden());
    patchAs(viewer, "/api/leads/" + id, Map.of("name", "Y")).andExpect(status().isForbidden());
    postAs(viewer, "/api/leads/" + id + "/activities", Map.of("type", "NOTE", "body", "x"))
        .andExpect(status().isForbidden());
    deleteAs(viewer, "/api/leads/" + id).andExpect(status().isForbidden());
  }

  @Test
  void onlyAdminsOwnersOrCreatorsCanDeleteALead() throws Exception {
    Session a = signIn(Role.REP);
    Session b = signIn(Role.REP);
    Session admin = signIn(Role.ADMIN);
    String id = createLead(a, Map.of("name", "Delete me")).get("id").asText();

    deleteAs(b, "/api/leads/" + id).andExpect(status().isForbidden());
    deleteAs(admin, "/api/leads/" + id).andExpect(status().isNoContent());
    getAs(a, "/api/leads/" + id).andExpect(status().isNotFound());
    Integer activities = jdbc.queryForObject("SELECT count(*) FROM activities", Integer.class);
    assertThat(activities).isZero();
  }

  @Test
  void theCreatorCanDeleteTheirOwnLead() throws Exception {
    Session a = signIn(Role.REP);
    String id = createLead(a, Map.of("name", "Mine")).get("id").asText();
    deleteAs(a, "/api/leads/" + id).andExpect(status().isNoContent());
  }

  @Test
  void rejectsAssigningALeadToAnInactiveUser() throws Exception {
    Session rep = signIn(Role.REP);
    Session other = signIn(Role.REP);
    jdbc.update("UPDATE users SET active = false WHERE id = ?", other.id());
    postAs(rep, "/api/leads", Map.of("name", "X", "ownerId", other.id().toString())).andExpect(status().isBadRequest());
  }
}
