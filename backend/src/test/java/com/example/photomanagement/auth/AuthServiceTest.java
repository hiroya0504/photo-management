package com.example.photomanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.photomanagement.auth.AuthProperties.JwtProps;
import com.example.photomanagement.auth.AuthProperties.PasswordProps;
import com.example.photomanagement.auth.AuthProperties.RefreshTokenProps;
import com.example.photomanagement.common.error.ConflictException;
import com.example.photomanagement.common.error.UnauthorizedException;
import com.example.photomanagement.user.RoleMapper;
import com.example.photomanagement.user.User;
import com.example.photomanagement.user.UserMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserMapper userMapper;
  @Mock private RoleMapper roleMapper;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private JwtService jwtService;

  private PasswordHasher passwordHasher;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    AuthProperties props =
        new AuthProperties(
            new JwtProps("test-secret-min-32-bytes-12345678901234", Duration.ofMinutes(15)),
            new RefreshTokenProps(
                Duration.ofDays(7),
                "refresh_token",
                "/api/auth/refresh",
                true,
                Duration.ofSeconds(5)),
            new PasswordProps(4)); // low cost for fast tests
    passwordHasher = new PasswordHasher(props);
    authService =
        new AuthService(userMapper, roleMapper, passwordHasher, refreshTokenService, jwtService);
  }

  @Test
  void signupCreatesUserAndAssignsUserRole() {
    when(userMapper.findActiveByEmail("alice@example.com"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(stubUser(10L, "alice@example.com", "hashed")));
    when(roleMapper.findIdByName("USER")).thenReturn(Optional.of((short) 2));

    User created = authService.signup("alice@example.com", "correct-horse");

    assertThat(created.id()).isEqualTo(10L);
    verify(userMapper).insert(eq("alice@example.com"), anyString());
    verify(roleMapper).assignRole(10L, (short) 2);
  }

  @Test
  void signupRejectsDuplicateEmail() {
    when(userMapper.findActiveByEmail("dup@example.com"))
        .thenReturn(Optional.of(stubUser(1L, "dup@example.com", "x")));

    assertThatThrownBy(() -> authService.signup("dup@example.com", "pw"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Email is already in use");

    verify(userMapper, never()).insert(anyString(), anyString());
    verify(roleMapper, never()).assignRole(any(), any());
  }

  @Test
  void signupConcurrentInsertThrowsConflictNotRawDataException() {
    // Race condition: another transaction inserted the same email between our findActiveByEmail
    // check and our insert. The partial unique index fires and Spring wraps it as
    // DuplicateKeyException. The user-facing API contract must still return EMAIL_TAKEN.
    when(userMapper.findActiveByEmail("race@example.com")).thenReturn(Optional.empty());
    doThrow(new DuplicateKeyException("partial unique index violation"))
        .when(userMapper)
        .insert(eq("race@example.com"), anyString());

    assertThatThrownBy(() -> authService.signup("race@example.com", "password"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Email is already in use");

    verify(roleMapper, never()).assignRole(any(), any());
  }

  @Test
  void authenticateSucceedsWithCorrectPassword() {
    String hash = passwordHasher.hash("correct-horse");
    when(userMapper.findActiveByEmail("bob@example.com"))
        .thenReturn(Optional.of(stubUser(20L, "bob@example.com", hash)));

    User user = authService.authenticate("bob@example.com", "correct-horse");

    assertThat(user.id()).isEqualTo(20L);
  }

  @Test
  void authenticateFailsWithWrongPassword() {
    String hash = passwordHasher.hash("correct-horse");
    when(userMapper.findActiveByEmail("bob@example.com"))
        .thenReturn(Optional.of(stubUser(20L, "bob@example.com", hash)));

    assertThatThrownBy(() -> authService.authenticate("bob@example.com", "wrong"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Email or password is incorrect");
  }

  @Test
  void authenticateFailsWhenUserMissing() {
    when(userMapper.findActiveByEmail("ghost@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.authenticate("ghost@example.com", "any"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Email or password is incorrect");
    // Sanity: error message must not distinguish "no such user" from "wrong password"
  }

  @Test
  void changePasswordSucceedsAndRevokesAllRefreshTokens() {
    String oldHash = passwordHasher.hash("old-pw");
    when(userMapper.findActiveById(30L))
        .thenReturn(Optional.of(stubUser(30L, "carol@example.com", oldHash)));

    authService.changePassword(30L, "old-pw", "new-pw");

    verify(userMapper, times(1)).updatePasswordHash(eq(30L), anyString());
    // Password change must invalidate every existing session on every device.
    verify(refreshTokenService, times(1)).revokeAllForUser(30L);
  }

  @Test
  void changePasswordFailsWithWrongOldPassword() {
    String oldHash = passwordHasher.hash("old-pw");
    when(userMapper.findActiveById(30L))
        .thenReturn(Optional.of(stubUser(30L, "carol@example.com", oldHash)));

    assertThatThrownBy(() -> authService.changePassword(30L, "wrong-old", "new-pw"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Current password is incorrect");

    verify(userMapper, never()).updatePasswordHash(any(), anyString());
    verify(refreshTokenService, never()).revokeAllForUser(any());
  }

  @Test
  void issueAccessTokenForLooksUpRolesAndSignsToken() {
    when(roleMapper.findRoleNamesByUserId(42L)).thenReturn(java.util.List.of("USER", "ADMIN"));
    when(jwtService.issueAccessToken(42L, java.util.List.of("USER", "ADMIN")))
        .thenReturn("signed-jwt-value");

    String token = authService.issueAccessTokenFor(42L);

    assertThat(token).isEqualTo("signed-jwt-value");
  }

  private static User stubUser(long id, String email, String passwordHash) {
    Instant now = Instant.now();
    return new User(id, email, passwordHash, now, now, null);
  }
}
