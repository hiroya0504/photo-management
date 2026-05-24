package com.example.photomanagement.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived access tokens signed with HS256. The Bearer token validation on incoming
 * requests is performed by Spring Security's resource server filter chain using the {@link
 * JwtDecoder} bean exposed from this class.
 */
@Service
public class JwtService {

  /** Claim name for the list of role names (without the {@code ROLE_} prefix). */
  public static final String CLAIM_ROLES = "roles";

  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final Duration accessTtl;

  public JwtService(AuthProperties props) {
    byte[] secretBytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
    // Intentional fail-fast at startup: HS256 requires a key of at least the hash size (256 bit).
    // A short secret silently degrades HMAC security, so we'd rather crash than boot mis-keyed.
    if (secretBytes.length < 32) {
      throw new IllegalStateException(
          "auth.jwt.secret must be at least 32 bytes (HS256). Got " + secretBytes.length + ".");
    }
    SecretKeySpec key = new SecretKeySpec(secretBytes, "HmacSHA256");
    this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    this.accessTtl = props.jwt().accessTtl();
  }

  public String issueAccessToken(Long userId, List<String> roleNames) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiresAt(now.plus(accessTtl))
            .claim(CLAIM_ROLES, roleNames)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /**
   * Exposed so Spring Security's resource server filter chain can validate Bearer tokens with the
   * same secret used to sign them.
   */
  public JwtDecoder jwtDecoder() {
    return decoder;
  }
}
