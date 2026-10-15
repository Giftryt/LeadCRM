package com.leadtracker;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leadtracker.user.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsersApiTest extends ApiTestBase {

  private static final Map<String, Object> NEHA =
      Map.of("name", "Neha", "email", "Neha@Example.com", "role", "REP", "password", "Welcome2026");

  @Test
  void adminsCreateUsersAndRepsCannot() throws Exception {
    Session admin = signIn(Role.ADMIN);
    Session rep = signIn(Role.REP);

    postAs(rep, "/api/users", NEHA).andExpect(status().isForbidden());
    postAs(admin, "/api/users", NEHA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("neha@example.com"))
        .andExpect(jsonPath("$.role").value("REP"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    postAs(admin, "/api/users", NEHA).andExpect(status().isConflict());
  }

  @Test
  void rejectsWeakPasswords() throws Exception {
    Session admin = signIn(Role.ADMIN);
    postAs(admin, "/api/users", Map.of("name", "A", "email", "a@example.com", "password", "short"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("validation_error"))
        .andExpect(jsonPath("$.error.details[0].field").value("password"));
    postAs(admin, "/api/users", Map.of("name", "A", "email", "a@example.com", "password", "onlyletterslong"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsAnUnknownRole() throws Exception {
    Session admin = signIn(Role.ADMIN);
    postAs(admin, "/api/users", Map.of("name", "A", "email", "a@example.com", "role", "BOSS", "password", "Welcome2026"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details[0].field").value("role"));
  }

  @Test
  void stopsAnAdminFromDeactivatingThemselves() throws Exception {
    Session admin = signIn(Role.ADMIN);
    patchAs(admin, "/api/users/" + admin.id(), Map.of("active", false)).andExpect(status().isBadRequest());
    patchAs(admin, "/api/users/" + admin.id(), Map.of("role", "REP")).andExpect(status().isBadRequest());
  }

  @Test
  void listsActiveUsersAndLetsAdminsSeeInactiveOnes() throws Exception {
    Session admin = signIn(Role.ADMIN, "Asha");
    Session rep = signIn(Role.REP, "Ravi");
    patchAs(admin, "/api/users/" + rep.id(), Map.of("active", false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    getAs(admin, "/api/users")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Asha"));
    getAs(admin, "/api/users?includeInactive=true").andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void returns404ForAnUnknownOrMalformedUserId() throws Exception {
    Session admin = signIn(Role.ADMIN);
    patchAs(admin, "/api/users/00000000-0000-0000-0000-000000000000", Map.of("name", "X"))
        .andExpect(status().isNotFound());
    patchAs(admin, "/api/users/not-a-uuid", Map.of("name", "X")).andExpect(status().isNotFound());
  }
}
