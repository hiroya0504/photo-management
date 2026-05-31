package com.example.photomanagement.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.photomanagement.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * HTTP-level tests for {@link AdminUserController}, focused on the authorisation matrix enforced by
 * {@code SecurityConfig} ({@code /api/admin/**} requires {@code ROLE_ADMIN}) and the admin
 * soft-delete behaviour. An admin token is produced by promoting a freshly signed-up user via
 * {@link RoleMapper} and then logging in again so the new role lands in the JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(statements = {"DELETE FROM refresh_tokens", "DELETE FROM user_roles", "DELETE FROM users"})
class AdminUserControllerTest {

  private static final String COOKIE_NAME = "refresh_token";
  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserMapper userMapper;
  @Autowired private RoleMapper roleMapper;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() throws Exception {
    String adminEmail = "admin-" + System.nanoTime() + "@example.com";
    signup(adminEmail, PASSWORD);
    Long adminId = userMapper.findActiveByEmail(adminEmail).orElseThrow().id();
    roleMapper.assignRole(adminId, roleMapper.findIdByName("ADMIN").orElseThrow());
    adminToken = tokenFrom(login(adminEmail, PASSWORD).andReturn());

    String userEmail = "plain-" + System.nanoTime() + "@example.com";
    userToken = tokenFrom(signup(userEmail, PASSWORD));
  }

  @Test
  void listUsersAsAdminReturnsAllActiveUsers() throws Exception {
    String extra = "extra-" + System.nanoTime() + "@example.com";
    signup(extra, PASSWORD);

    mockMvc
        .perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].email", hasItem(extra)))
        .andExpect(jsonPath("$[0].id").exists());
  }

  @Test
  void listUsersAsNonAdminReturnsForbidden() throws Exception {
    mockMvc
        .perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void listUsersWithoutTokenReturnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteUserAsAdminSoftDeletesTarget() throws Exception {
    String targetEmail = "target-" + System.nanoTime() + "@example.com";
    signup(targetEmail, PASSWORD);
    Long targetId = userMapper.findActiveByEmail(targetEmail).orElseThrow().id();

    mockMvc
        .perform(
            delete("/api/admin/users/" + targetId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNoContent());

    assertThat(userMapper.findActiveById(targetId)).isEmpty();
  }

  @Test
  void deleteUserRevokesTargetSessions() throws Exception {
    String targetEmail = "victim-" + System.nanoTime() + "@example.com";
    MvcResult targetSignup = signup(targetEmail, PASSWORD);
    Long targetId = userMapper.findActiveByEmail(targetEmail).orElseThrow().id();
    var refresh = targetSignup.getResponse().getCookie(COOKIE_NAME);

    mockMvc
        .perform(
            delete("/api/admin/users/" + targetId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteUserAsNonAdminReturnsForbidden() throws Exception {
    Long someId = userMapper.findActiveByEmail(currentUserEmail()).orElseThrow().id();

    mockMvc
        .perform(
            delete("/api/admin/users/" + someId).header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteMissingUserReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            delete("/api/admin/users/999999999").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode", equalTo("NOT_FOUND")));
  }

  // -- helpers -------------------------------------------------------------

  /** Any active email; used only to obtain a valid id for the non-admin DELETE authz check. */
  private String currentUserEmail() throws Exception {
    return userMapper.listActive(1, 0).get(0).email();
  }

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
    return mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password)))
        .andExpect(status().isOk());
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
