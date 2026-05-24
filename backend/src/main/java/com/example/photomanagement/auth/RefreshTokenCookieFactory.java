package com.example.photomanagement.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code Set-Cookie} header used for the refresh token. Centralised so the attributes
 * ({@code HttpOnly}, {@code Secure}, {@code SameSite=Strict}, path-scoped) are identical at login,
 * refresh and logout.
 */
@Component
public class RefreshTokenCookieFactory {

  private final AuthProperties props;

  public RefreshTokenCookieFactory(AuthProperties props) {
    this.props = props;
  }

  public ResponseCookie create(NewRefreshToken token) {
    return baseBuilder(token.plaintext()).maxAge(props.refreshToken().ttl()).build();
  }

  /** Cookie that immediately clears the refresh token from the browser. Used on logout. */
  public ResponseCookie expire() {
    return baseBuilder("").maxAge(Duration.ZERO).build();
  }

  public String cookieName() {
    return props.refreshToken().cookieName();
  }

  private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
    return ResponseCookie.from(props.refreshToken().cookieName(), value)
        .httpOnly(true)
        .secure(props.refreshToken().secure())
        .sameSite("Strict")
        .path(props.refreshToken().cookiePath());
  }
}
