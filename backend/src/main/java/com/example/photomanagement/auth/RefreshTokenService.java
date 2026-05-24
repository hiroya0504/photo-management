package com.example.photomanagement.auth;

import com.example.photomanagement.common.error.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates and revokes refresh tokens. Implements RFC 6749-style refresh-token rotation with
 * theft / replay detection by "family revocation": any reuse of an already-used token (outside a
 * small grace window) revokes every active token sharing the same {@code family_id}.
 *
 * <p>Plan reference: 7.8.3 / 7.8.4.
 */
@Service
public class RefreshTokenService {

  /** Number of random bytes per token before base64url encoding. */
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenMapper mapper;
  private final AuthProperties props;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

  public RefreshTokenService(RefreshTokenMapper mapper, AuthProperties props, Clock clock) {
    this.mapper = mapper;
    this.props = props;
    this.clock = clock;
  }

  /** Starts a brand-new family. Used after a successful login. */
  @Transactional
  public NewRefreshToken issueForLogin(Long userId) {
    return issue(userId, UUID.randomUUID());
  }

  /**
   * Consumes an existing refresh token and produces a new one in the same family. Detects reuse of
   * already-consumed tokens and revokes the entire family in that case.
   */
  @Transactional
  public RotationResult rotate(String plaintextToken) {
    String tokenHash = sha256Hex(plaintextToken);
    RefreshTokenRecord record =
        mapper
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () -> new UnauthorizedException("INVALID_REFRESH", "Unknown refresh token"));
    Instant now = clock.instant();

    if (record.revokedAt() != null) {
      throw new UnauthorizedException("REVOKED_REFRESH", "Refresh token has been revoked");
    }
    if (!now.isBefore(record.expiresAt())) {
      throw new UnauthorizedException("EXPIRED_REFRESH", "Refresh token expired");
    }

    if (record.usedAt() != null) {
      return handleAlreadyUsed(record, now);
    }

    // Atomic "mark used"; the WHERE used_at IS NULL guard ensures only one of any concurrent
    // rotations of the same plaintext wins. The loser sees affected-rows = 0 and falls back to
    // the same path as a deliberate reuse (grace window or family revocation).
    int marked = mapper.markUsed(record.id(), now);
    if (marked == 0) {
      RefreshTokenRecord refreshed =
          mapper
              .findByTokenHash(tokenHash)
              .orElseThrow(
                  () -> new UnauthorizedException("INVALID_REFRESH", "Unknown refresh token"));
      return handleAlreadyUsed(refreshed, now);
    }

    NewRefreshToken issued = issue(record.userId(), record.familyId());
    return new RotationResult(record.userId(), issued);
  }

  /**
   * Decides what to do when a refresh token is presented after it has already been consumed: within
   * the grace window we assume a benign retry (issue a new token, family stays alive); outside it
   * we treat the reuse as theft and revoke the whole family.
   */
  private RotationResult handleAlreadyUsed(RefreshTokenRecord record, Instant now) {
    Instant usedAt = record.usedAt();
    if (usedAt == null) {
      // Race: someone reverted used_at between markUsed and our re-read. Defensive: treat as theft.
      mapper.revokeFamily(record.familyId(), now);
      throw new UnauthorizedException(
          "TOKEN_REUSE", "Refresh token reuse detected; family revoked");
    }
    Duration sinceUsed = Duration.between(usedAt, now);
    if (sinceUsed.compareTo(props.refreshToken().graceWindow()) > 0) {
      mapper.revokeFamily(record.familyId(), now);
      throw new UnauthorizedException(
          "TOKEN_REUSE", "Refresh token reuse detected; family revoked");
    }
    NewRefreshToken issued = issue(record.userId(), record.familyId());
    return new RotationResult(record.userId(), issued);
  }

  /** Revokes only the supplied token. Other family members (e.g. another device) remain valid. */
  @Transactional
  public void revoke(String plaintextToken) {
    String tokenHash = sha256Hex(plaintextToken);
    Optional<RefreshTokenRecord> maybeRecord = mapper.findByTokenHash(tokenHash);
    if (maybeRecord.isEmpty()) {
      // Already gone (e.g. logout retried) — nothing to do.
      return;
    }
    mapper.revokeById(maybeRecord.get().id(), clock.instant());
  }

  /**
   * Revokes every active refresh token for a user (all devices). Called when an event invalidates
   * trust in existing sessions — most notably a password change.
   *
   * <p>Note: outstanding access tokens (JWTs) cannot be revoked and remain valid until their expiry
   * (max 15 min). This is the documented trade-off of stateless access tokens.
   */
  @Transactional
  public int revokeAllForUser(Long userId) {
    return mapper.revokeAllForUser(userId, clock.instant());
  }

  private NewRefreshToken issue(Long userId, UUID familyId) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(props.refreshToken().ttl());
    String plaintext = generatePlaintext();
    mapper.insert(userId, familyId, sha256Hex(plaintext), expiresAt);
    return new NewRefreshToken(plaintext, expiresAt);
  }

  private String generatePlaintext() {
    byte[] buf = new byte[TOKEN_BYTES];
    random.nextBytes(buf);
    return base64Url.encodeToString(buf);
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
