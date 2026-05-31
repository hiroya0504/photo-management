package com.example.photomanagement.user;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.photomanagement.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HTTP-level tests for {@link UserController}. Runs the full security chain against a real Postgres
 * (Testcontainers): a Bearer access token is obtained via {@code /api/auth/signup}, then used to
 * exercise the self-profile endpoints. Verifies authn enforcement, ownership-from-token, the
 * email-conflict path and that password change / account deletion revoke existing sessions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(statements = {"DELETE FROM refresh_tokens", "DELETE FROM user_roles", "DELETE FROM users"})
class UserControllerTest {

  private static final String COOKIE_NAME = "refresh_token";
  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private String uniqueEmail;

  @BeforeEach
  void setUp() {
    uniqueEmail = "user-" + System.nanoTime() + "@example.com";
  }

  @Test
  void getMeReturnsProfileForBearerToken() throws Exception {
    String token = tokenFrom(signup(uniqueEmail, PASSWORD));

    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", notNullValue()))
        .andExpect(jsonPath("$.email", equalTo(uniqueEmail)))
        .andExpect(jsonPath("$.roles", contains("USER")))
        .andExpect(jsonPath("$.createdAt", notNullValue()));
  }

  @Test
  void getMeWithoutTokenReturnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void patchMeUpdatesEmailAndIsReflected() throws Exception {
    String token = tokenFrom(signup(uniqueEmail, PASSWORD));
    String newEmail = "renamed-" + System.nanoTime() + "@example.com";

    mockMvc
        .perform(
            patch("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", newEmail)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email", equalTo(newEmail)));

    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email", equalTo(newEmail)));
  }

  @Test
  void patchMeRejectsInvalidEmail() throws Exception {
    String token = tokenFrom(signup(uniqueEmail, PASSWORD));

    mockMvc
        .perform(
            patch("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", "not-an-email")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_ERROR")));
  }

  @Test
  void patchMeToAnExistingEmailReturnsConflict() throws Exception {
    String otherEmail = "taken-" + System.nanoTime() + "@example.com";
    signup(otherEmail, PASSWORD);
    String token = tokenFrom(signup(uniqueEmail, PASSWORD));

    mockMvc
        .perform(
            patch("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", otherEmail)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode", equalTo("EMAIL_TAKEN")));
  }

  @Test
  void changePasswordSwapsTheCredential() throws Exception {
    String token = tokenFrom(signup(uniqueEmail, PASSWORD));
    String newPassword = "another-strong-password";

    mockMvc
        .perform(
            post("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("oldPassword", PASSWORD, "newPassword", newPassword)))
        .andExpect(status().isNoContent());

    login(uniqueEmail, PASSWORD).andExpect(status().isUnauthorized());
    login(uniqueEmail, newPassword).andExpect(status().isOk());
  }

  @Test
  void changePasswordWithWrongOldReturnsUnauthorized() throws Exception {
    String token = tokenFrom(signup(uniqueEmail, PASSWORD));

    mockMvc
        .perform(
            post("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json("oldPassword", "wrong-password", "newPassword", "brand-new-password")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode", equalTo("INVALID_CREDENTIALS")));
  }

  @Test
  void changePasswordRevokesExistingRefreshSessions() throws Exception {
    MvcResult signup = signup(uniqueEmail, PASSWORD);
    String token = tokenFrom(signup);
    Cookie refresh = signup.getResponse().getCookie(COOKIE_NAME);

    mockMvc
        .perform(
            post("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("oldPassword", PASSWORD, "newPassword", "yet-another-password")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refresh))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode", equalTo("REVOKED_REFRESH")));
  }

  @Test
  void deleteMeSoftDeletesAndFreesTheEmail() throws Exception {
    String token = tokenFrom(signup(uniqueEmail, PASSWORD));

    mockMvc
        .perform(delete("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    // The token is structurally valid but the user row is gone -> treated as unauthenticated.
    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode", equalTo("USER_GONE")));

    // The address is reusable because the unique index is scoped to active rows.
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", uniqueEmail, "password", PASSWORD)))
        .andExpect(status().isCreated());
  }

  @Test
  void deleteMeRevokesExistingRefreshSessions() throws Exception {
    MvcResult signup = signup(uniqueEmail, PASSWORD);
    String token = tokenFrom(signup);
    Cookie refresh = signup.getResponse().getCookie(COOKIE_NAME);

    mockMvc
        .perform(delete("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
  }

  // -- helpers -------------------------------------------------------------

  private MvcResult signup(String email, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password)))
        .andExpect(status().isCreated())
        .andReturn();
  }

  private org.springframework.test.web.servlet.ResultActions login(String email, String password)
      throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json("email", email, "password", password)));
  }

  private String tokenFrom(MvcResult res) throws Exception {
    return objectMapper
        .readTree(res.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }

  private String json(String... kv) throws Exception {
    java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      map.put(kv[i], kv[i + 1]);
    }
    return objectMapper.writeValueAsString(map);
  }
}
