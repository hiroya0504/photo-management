package com.example.photomanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.example.photomanagement.auth.AuthProperties.JwtProps;
import com.example.photomanagement.auth.AuthProperties.PasswordProps;
import com.example.photomanagement.auth.AuthProperties.RefreshTokenProps;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtServiceTest {

  private static final String VALID_SECRET = "a-super-long-secret-of-at-least-32-bytes!!!";

  @Test
  void issuedTokenCarriesSubjectExpiryAndRoles() {
    JwtService jwt = newService(VALID_SECRET, Duration.ofMinutes(15));

    String token = jwt.issueAccessToken(42L, List.of("USER", "ADMIN"));
    Jwt parsed = jwt.jwtDecoder().decode(token);

    assertThat(parsed.getSubject()).isEqualTo("42");
    assertThat(parsed.<List<String>>getClaim(JwtService.CLAIM_ROLES))
        .containsExactlyInAnyOrder("USER", "ADMIN");
    assertThat(parsed.getExpiresAt())
        .isCloseTo(Instant.now().plus(15, ChronoUnit.MINUTES), within(5, ChronoUnit.SECONDS));
  }

  @Test
  void shortSecretIsRejectedAtStartup() {
    assertThatThrownBy(() -> newService("too-short", Duration.ofMinutes(15)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 32 bytes");
  }

  @Test
  void tamperedTokenFailsValidation() {
    JwtService jwt = newService(VALID_SECRET, Duration.ofMinutes(15));
    String token = jwt.issueAccessToken(1L, List.of("USER"));

    // Flip the FIRST character of the signature segment. The last base64url char of a 32-byte
    // HMAC signature only encodes 4 significant bits, so flipping it (e.g. 'A'<->'B') can decode
    // to the same signature and leave the token valid — that made the old "flip the last char"
    // approach fail ~1 in 16 runs. Any non-final char carries a full 6 bits, so changing it is
    // guaranteed to alter the signature and fail verification.
    int sigStart = token.lastIndexOf('.') + 1;
    char first = token.charAt(sigStart);
    char different = (first == 'A') ? 'B' : 'A';
    String tampered = token.substring(0, sigStart) + different + token.substring(sigStart + 1);

    assertThatThrownBy(() -> jwt.jwtDecoder().decode(tampered))
        .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
  }

  private static JwtService newService(String secret, Duration ttl) {
    AuthProperties props =
        new AuthProperties(
            new JwtProps(secret, ttl),
            new RefreshTokenProps(
                Duration.ofDays(7),
                "refresh_token",
                "/api/auth/refresh",
                true,
                Duration.ofSeconds(5)),
            new PasswordProps(4));
    return new JwtService(props);
  }
}
