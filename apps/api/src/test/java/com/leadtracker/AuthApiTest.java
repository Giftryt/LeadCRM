package com.leadtracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leadtracker.user.Role;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends ApiTestBase {

  @Test
  void signsInWithACookieAndReadsMe() throws Exception {
    Session s = signIn(Role.REP, "Priya");
    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", s.email(), "password", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value("Priya"))
            .andExpect(jsonPath("$.user.role").value("REP"))
            .andReturn();
    String setCookie = login.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie).contains("lt_session=").contains("HttpOnly").contains("SameSite=Lax");

    String token = json.readTree(login.getResponse().getContentAsString()).get("token").asText();
    mvc.perform(get("/api/auth/me").cookie(new Cookie("lt_session", token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(s.id().toString()))
        .andExpect(jsonPath("$.user.email").value(s.email()));
  }

  @Test
  void signOutClearsTheCookie() throws Exception {
    MvcResult r = mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent()).andReturn();
    assertThat(r.getResponse().getHeader("Set-Cookie")).contains("lt_session=").contains("Max-Age=0");
  }

  @Test
  void rejectsAWrongPasswordWithAGenericMessage() throws Exception {
    Session s = signIn(Role.REP);
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", s.email(), "password", "nope"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value("Email or password is incorrect."));
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "nobody@example.com", "password", "nope"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value("Email or password is incorrect."));
  }

  @Test
  void requiresASessionForProtectedRoutes() throws Exception {
    mvc.perform(get("/api/leads"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("unauthorized"));
    mvc.perform(get("/api/leads").header("Authorization", "Bearer not-a-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value("Your session has expired. Sign in again."));
  }

  @Test
  void blocksDeactivatedUsersImmediately() throws Exception {
    Session s = signIn(Role.REP);
    jdbc.update("UPDATE users SET active = false WHERE id = ?", s.id());
    getAs(s, "/api/leads")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value("Your account is not active. Contact an admin."));
  }

  @Test
  void changesPasswordWithTheCurrentPassword() throws Exception {
    Session s = signIn(Role.REP);
    postAs(s, "/api/auth/change-password", Map.of("currentPassword", "wrong", "newPassword", "NewPassword99"))
        .andExpect(status().isBadRequest());
    postAs(s, "/api/auth/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "short"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details[0].field").value("newPassword"));
    postAs(s, "/api/auth/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "NewPassword99"))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", s.email(), "password", "NewPassword99"))))
        .andExpect(status().isOk());
  }

  @Test
  void healthNeedsNoSignIn() throws Exception {
    mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
  }
}
