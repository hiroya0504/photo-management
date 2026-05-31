package com.example.photomanagement.user;

/**
 * Port (owned by the {@code user} feature) for changing a user's password.
 *
 * <p>The {@code POST /api/users/me/password} endpoint lives in {@code user}, but the actual change
 * is BCrypt-heavy and intertwined with session revocation, so it is implemented in {@code auth}'s
 * {@code AuthService}. As with {@link SessionRevoker}, routing the call through a {@code
 * user}-owned port keeps the only dependency edge at {@code auth -> user} and avoids a feature
 * cycle. The BCrypt primitive ({@code PasswordHasher}) never crosses the boundary — it stays inside
 * {@code auth}. See ADR 0005.
 */
public interface PasswordChanger {

  /**
   * Verifies {@code oldPlainPassword}, sets {@code newPlainPassword}, and revokes the user's
   * existing sessions so they cannot outlive the credential change.
   *
   * @throws com.example.photomanagement.common.error.UnauthorizedException if the user is gone or
   *     the old password does not match
   */
  void changePassword(Long userId, String oldPlainPassword, String newPlainPassword);
}
