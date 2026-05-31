package com.example.photomanagement.user;

/**
 * Port (owned by the {@code user} feature) for revoking all of a user's active sessions.
 *
 * <p>Account operations such as deletion need to terminate the user's refresh-token sessions, but
 * that machinery lives in the {@code auth} feature — and {@code auth} already depends on {@code
 * user} ({@code AuthService} reads {@link User}). Having {@code user} call {@code auth} directly
 * would create a feature-package cycle that {@code ArchitectureTest.noFeatureCycles} forbids.
 *
 * <p>So {@code user} declares the capability it needs as this interface and {@code auth}'s {@code
 * RefreshTokenService} implements it. The only resulting dependency edge is {@code auth -> user}
 * (the implementor depending on the port), which is acyclic. This is dependency inversion: the
 * consumer owns the abstraction, the provider supplies the implementation. See ADR 0005.
 */
public interface SessionRevoker {

  /**
   * Revokes every active refresh token for the user (all devices). Outstanding access tokens (JWTs)
   * remain valid until their short natural expiry — the documented trade-off of stateless tokens.
   *
   * @return the number of tokens revoked
   */
  int revokeAllForUser(Long userId);
}
