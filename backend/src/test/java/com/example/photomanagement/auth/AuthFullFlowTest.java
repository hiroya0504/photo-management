package com.example.photomanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.photomanagement.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
 * End-to-end happy path across the auth + user features against a real Postgres (Testcontainers):
 * signup -> /users/me -> login -> refresh -> /users/me (with the rotated access token) -> logout,
 * then proves the rotated refresh cookie is dead after logout. This is the automated form of the
 * manual curl scenario in the M2 plan (7.8.14 / 7.9.6); the per-endpoint edge cases live in the
 * dedicated controller tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(statements = {"DELETE FROM refresh_tokens", "DELETE FROM user_roles", "DELETE FROM users"})
class AuthFullFlowTest {

  private static final String COOKIE_NAME = "refresh_token";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void signupLoginRefreshAndLogoutFullFlow() throws Exception {
    String email = "flow-" + System.nanoTime() + "@example.com";
    String password = "correct-horse-battery";

    // 1. Signup -> access token + refresh cookie.
    MvcResult signup =
        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", email, "password", password)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken", notNullValue()))
            .andReturn();
    String signupToken = tokenFrom(signup);

    // 2. The signup access token works against a protected endpoint.
    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + signupToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email", equalTo(email)));

    // 3. Login issues a fresh token + cookie (new family).
    MvcResult login =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", email, "password", password)))
            .andExpect(status().isOk())
            .andReturn();
    Cookie loginCookie = login.getResponse().getCookie(COOKIE_NAME);
    assertThat(loginCookie).isNotNull();

    // 4. Refresh rotates the cookie and mints a new access token.
    MvcResult refresh =
        mockMvc
            .perform(post("/api/auth/refresh").cookie(loginCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", notNullValue()))
            .andReturn();
    String refreshedToken = tokenFrom(refresh);
    Cookie rotatedCookie = refresh.getResponse().getCookie(COOKIE_NAME);
    assertThat(rotatedCookie).isNotNull();
    assertThat(rotatedCookie.getValue()).isNotEqualTo(loginCookie.getValue());

    // 5. The refreshed access token also authenticates.
    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + refreshedToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email", equalTo(email)));

    // 6. Logout clears the cookie and revokes the row server-side.
    mockMvc
        .perform(post("/api/auth/logout").cookie(rotatedCookie))
        .andExpect(status().isNoContent());

    // 7. The rotated cookie is now dead.
    mockMvc
        .perform(post("/api/auth/refresh").cookie(rotatedCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode", equalTo("REVOKED_REFRESH")));
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
