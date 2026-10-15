package com.leadtracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadtracker.common.Dates;
import com.leadtracker.user.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Shared setup: a clean database before each test and helpers to act as a signed-in user. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestBase {

  protected static final String PASSWORD = "Password123";
  /** Low-cost hash so tests stay fast; the app verifies any BCrypt cost. */
  private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
  private static final AtomicInteger COUNTER = new AtomicInteger();

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ObjectMapper json;

  /** A signed-in test user. */
  protected record Session(UUID id, String email, String name, String token) {}

  @BeforeEach
  void cleanDatabase() {
    jdbc.execute("TRUNCATE TABLE activities, leads, users CASCADE");
  }

  protected static LocalDate today() {
    return Dates.today(Clock.systemUTC(), ZoneId.of("Asia/Kolkata"));
  }

  protected Session signIn(Role role) throws Exception {
    return signIn(role, null);
  }

  protected Session signIn(Role role, String name) throws Exception {
    int n = COUNTER.incrementAndGet();
    String email = role.name().toLowerCase() + n + "@example.com";
    String displayName = name != null ? name : role.name() + " " + n;
    UUID id = createUser(email, displayName, role);
    String body = json.writeValueAsString(Map.of("email", email, "password", PASSWORD));
    String response =
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Session(id, email, displayName, json.readTree(response).get("token").asText());
  }

  protected UUID createUser(String email, String name, Role role) {
    return jdbc.queryForObject(
        "INSERT INTO users (email, name, password_hash, role) VALUES (?, ?, ?, ?) RETURNING id",
        UUID.class,
        email,
        name,
        PASSWORD_HASH,
        role.name());
  }

  /* ---------- request helpers ---------- */

  protected ResultActions getAs(Session s, String url) throws Exception {
    return mvc.perform(auth(get(url), s));
  }

  protected ResultActions postAs(Session s, String url, Object body) throws Exception {
    return mvc.perform(auth(post(url), s).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
  }

  protected ResultActions patchAs(Session s, String url, Object body) throws Exception {
    return mvc.perform(auth(patch(url), s).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
  }

  protected ResultActions deleteAs(Session s, String url) throws Exception {
    return mvc.perform(auth(delete(url), s));
  }

  protected static <T extends MockHttpServletRequestBuilder> T auth(T builder, Session s) {
    builder.header("Authorization", "Bearer " + s.token());
    return builder;
  }

  protected JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  /** Creates a lead and returns its JSON. */
  protected JsonNode createLead(Session s, Map<String, Object> fields) throws Exception {
    return body(postAs(s, "/api/leads", fields).andExpect(status().isCreated()));
  }
}
