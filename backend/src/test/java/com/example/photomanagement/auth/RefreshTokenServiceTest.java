package com.example.photomanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.photomanagement.TestcontainersConfiguration;
import com.example.photomanagement.auth.AuthProperties.JwtProps;
import com.example.photomanagement.auth.AuthProperties.PasswordProps;
import com.example.photomanagement.auth.AuthProperties.RefreshTokenProps;
import com.example.photomanagement.common.error.UnauthorizedException;
import com.example.photomanagement.user.UserMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RefreshTokenServiceTest {

  @Autowired private RefreshTokenMapper refreshTokenMapper;
  @Autowired private UserMapper userMapper;

  private MutableClock clock;
  private RefreshTokenService service;
  private Long userId;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-06-01T10:00:00Z"));
    AuthProperties props =
        new AuthProperties(
            new JwtProps("not-used-here-but-required-32-bytes!!", Duration.ofMinutes(15)),
            new RefreshTokenProps(
                Duration.ofDays(7),
                "refresh_token",
                "/api/auth/refresh",
                true,
                Duration.ofSeconds(5)),
            new PasswordProps(4));
    service = new RefreshTokenService(refreshTokenMapper, props, clock);

    String email = "rt-test+" + System.nanoTime() + "@example.com";
    userMapper.insert(email, "hash");
    userId = userMapper.findActiveByEmail(email).orElseThrow().id();
  }

  @Test
  void issueForLoginCreatesActiveTokenInNewFamily() {
    NewRefreshToken issued = service.issueForLogin(userId);

    RefreshTokenRecord stored =
        refreshTokenMapper.findByTokenHash(sha256Hex(issued.plaintext())).orElseThrow();
    assertThat(stored.userId()).isEqualTo(userId);
    assertThat(stored.usedAt()).isNull();
    assertThat(stored.revokedAt()).isNull();
    assertThat(stored.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
  }

  @Test
  void rotateMarksOldUsedAndIssuesNewInSameFamily() {
    NewRefreshToken initial = service.issueForLogin(userId);
    RefreshTokenRecord initialRecord =
        refreshTokenMapper.findByTokenHash(sha256Hex(initial.plaintext())).orElseThrow();

    clock.advance(Duration.ofSeconds(1));
    RotationResult result = service.rotate(initial.plaintext());

    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.refreshToken().plaintext()).isNotEqualTo(initial.plaintext());

    RefreshTokenRecord oldAfter =
        refreshTokenMapper.findByTokenHash(sha256Hex(initial.plaintext())).orElseThrow();
    assertThat(oldAfter.usedAt()).isNotNull();
    assertThat(oldAfter.revokedAt()).isNull();

    RefreshTokenRecord newRecord =
        refreshTokenMapper
            .findByTokenHash(sha256Hex(result.refreshToken().plaintext()))
            .orElseThrow();
    assertThat(newRecord.familyId()).isEqualTo(initialRecord.familyId());
  }

  @Test
  void rotateAfterReuseOutsideGraceRevokesEntireFamily() {
    NewRefreshToken a = service.issueForLogin(userId);
    clock.advance(Duration.ofSeconds(1));
    RotationResult b = service.rotate(a.plaintext()); // legitimate rotation; A is now used

    clock.advance(Duration.ofSeconds(10)); // beyond 5s grace window

    assertThatThrownBy(() -> service.rotate(a.plaintext()))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("reuse");

    // B is in the same family and should now also be revoked.
    RefreshTokenRecord bRecord =
        refreshTokenMapper.findByTokenHash(sha256Hex(b.refreshToken().plaintext())).orElseThrow();
    assertThat(bRecord.revokedAt()).isNotNull();
  }

  @Test
  void rotateWithinGraceWindowIssuesNewWithoutFamilyKill() {
    NewRefreshToken a = service.issueForLogin(userId);
    clock.advance(Duration.ofSeconds(1));
    RotationResult b = service.rotate(a.plaintext());

    clock.advance(Duration.ofSeconds(2)); // still within 5s of A's used_at
    RotationResult c = service.rotate(a.plaintext());

    // Family must remain alive: B should still be active.
    RefreshTokenRecord bRecord =
        refreshTokenMapper.findByTokenHash(sha256Hex(b.refreshToken().plaintext())).orElseThrow();
    assertThat(bRecord.revokedAt()).isNull();

    // C is freshly issued.
    assertThat(c.refreshToken().plaintext()).isNotEqualTo(b.refreshToken().plaintext());
  }

  @Test
  void rotateRevokedTokenFails() {
    NewRefreshToken a = service.issueForLogin(userId);
    service.revoke(a.plaintext());

    assertThatThrownBy(() -> service.rotate(a.plaintext()))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("revoked");
  }

  @Test
  void rotateExpiredTokenFails() {
    NewRefreshToken a = service.issueForLogin(userId);
    clock.advance(Duration.ofDays(8));

    assertThatThrownBy(() -> service.rotate(a.plaintext()))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void rotateUnknownTokenFails() {
    assertThatThrownBy(() -> service.rotate("totally-bogus-token"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Unknown");
  }

  @Test
  void revokeMarksOnlyTheSuppliedToken() {
    NewRefreshToken a = service.issueForLogin(userId);
    NewRefreshToken b = service.issueForLogin(userId); // separate login (different family)

    service.revoke(a.plaintext());

    assertThat(
            refreshTokenMapper.findByTokenHash(sha256Hex(a.plaintext())).orElseThrow().revokedAt())
        .isNotNull();
    assertThat(
            refreshTokenMapper.findByTokenHash(sha256Hex(b.plaintext())).orElseThrow().revokedAt())
        .isNull();
  }

  private static String sha256Hex(String s) {
    try {
      var md = java.security.MessageDigest.getInstance("SHA-256");
      return java.util.HexFormat.of()
          .formatHex(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Test clock whose "now" can be advanced manually. */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant initial) {
      this.now = initial;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
