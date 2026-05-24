package com.example.photomanagement.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the {@code auth.*} configuration tree.
 *
 * <p>Plan reference: 7.8.10. Activated through {@code @ConfigurationPropertiesScan} on the main
 * application class.
 */
@ConfigurationProperties("auth")
public record AuthProperties(JwtProps jwt, RefreshTokenProps refreshToken, PasswordProps password) {

  public record JwtProps(String secret, Duration accessTtl) {}

  public record RefreshTokenProps(
      Duration ttl, String cookieName, String cookiePath, boolean secure, Duration graceWindow) {}

  public record PasswordProps(int bcryptStrength) {}
}
