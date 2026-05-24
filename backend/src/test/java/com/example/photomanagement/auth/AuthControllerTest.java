package com.example.photomanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * HTTP-level tests for {@link AuthController}. Verifies status codes, JSON bodies and the
 * Set-Cookie wiring (including that the {@code @CookieValue("${auth.refresh-token.cookie-name}")}
 * placeholder resolves at startup), which unit tests on the services alone cannot catch.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(statements = {"DELETE FROM refresh_tokens", "DELETE FROM user_roles", "DELETE FROM users"})
class AuthControllerTest {

  private static final String COOKIE_NAME = "refresh_token";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private String uniqueEmail;

  @BeforeEach
  void setUp() {
    uniqueEmail = "ctrl-" + System.nanoTime() + "@example.com";
  }

  @Test
  void signupReturnsAccessTokenAndSetsRefreshCookie() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", uniqueEmail, "password", "correct-horse-battery")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken", notNullValue()))
        .andExpect(cookie().exists(COOKIE_NAME))
        .andExpect(cookie().httpOnly(COOKIE_NAME, true))
        .andExpect(cookie().path(COOKIE_NAME, "/api/auth/refresh"));
  }

  @Test
  void signupRejectsInvalidEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", "not-an-email", "password", "correct-horse-battery")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", equalTo("VALIDATION_ERROR")));
  }

  @Test
  void signupTwiceWithSameEmailReturnsConflict() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", uniqueEmail, "password", "correct-horse-battery")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", uniqueEmail, "password", "another-good-password")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode", equalTo("EMAIL_TAKEN")));
  }

  @Test
  void loginAfterSignupReturnsAccessTokenAndNewCookie() throws Exception {
    String password = "correct-horse-battery";
    signup(uniqueEmail, password);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", uniqueEmail, "password", password)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken", notNullValue()))
        .andExpect(cookie().exists(COOKIE_NAME));
  }

  @Test
  void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
    signup(uniqueEmail, "correct-horse-battery");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", uniqueEmail, "password", "wrong-password")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode", equalTo("INVALID_CREDENTIALS")));
  }

  @Test
  void refreshRotatesCookieAndIssuesNewAccessToken() throws Exception {
    Cookie initial = signup(uniqueEmail, "correct-horse-battery");

    MvcResult res =
        mockMvc
            .perform(post("/api/auth/refresh").cookie(initial))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", notNullValue()))
            .andExpect(cookie().exists(COOKIE_NAME))
            .andReturn();

    String rotated = res.getResponse().getCookie(COOKIE_NAME).getValue();
    assertThat(rotated).isNotEqualTo(initial.getValue());
  }

  @Test
  void refreshWithoutCookieReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(post("/api/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode", equalTo("MISSING_REFRESH")));
  }

  @Test
  void logoutClearsCookieEvenWithoutAuthorizationHeader() throws Exception {
    Cookie initial = signup(uniqueEmail, "correct-horse-battery");

    // No Authorization header (simulating expired access token). Must still succeed and clear
    // cookie.
    mockMvc
        .perform(post("/api/auth/logout").cookie(initial))
        .andExpect(status().isNoContent())
        .andExpect(
            header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
  }

  // -- helpers -------------------------------------------------------------

  private Cookie signup(String email, String password) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", email, "password", password)))
            .andExpect(status().isCreated())
            .andReturn();
    return res.getResponse().getCookie(COOKIE_NAME);
  }

  private String json(String... kv) throws Exception {
    java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      map.put(kv[i], kv[i + 1]);
    }
    return objectMapper.writeValueAsString(map);
  }
}
