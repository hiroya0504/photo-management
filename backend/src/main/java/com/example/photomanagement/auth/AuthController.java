package com.example.photomanagement.auth;

import com.example.photomanagement.auth.dto.LoginRequest;
import com.example.photomanagement.auth.dto.LoginResponse;
import com.example.photomanagement.auth.dto.RefreshResponse;
import com.example.photomanagement.auth.dto.SignupRequest;
import com.example.photomanagement.common.error.UnauthorizedException;
import com.example.photomanagement.user.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenService refreshTokenService;
  private final RefreshTokenCookieFactory cookieFactory;

  public AuthController(
      AuthService authService,
      RefreshTokenService refreshTokenService,
      RefreshTokenCookieFactory cookieFactory) {
    this.authService = authService;
    this.refreshTokenService = refreshTokenService;
    this.cookieFactory = cookieFactory;
  }

  @PostMapping("/signup")
  public ResponseEntity<LoginResponse> signup(
      @Valid @RequestBody SignupRequest req, HttpServletResponse response) {
    User user = authService.signup(req.email(), req.password());
    String access = authService.issueAccessTokenFor(user.id());
    NewRefreshToken refresh = refreshTokenService.issueForLogin(user.id());
    response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.create(refresh).toString());
    return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(access));
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest req, HttpServletResponse response) {
    User user = authService.authenticate(req.email(), req.password());
    String access = authService.issueAccessTokenFor(user.id());
    NewRefreshToken refresh = refreshTokenService.issueForLogin(user.id());
    response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.create(refresh).toString());
    return ResponseEntity.ok(new LoginResponse(access));
  }

  @PostMapping("/refresh")
  public ResponseEntity<RefreshResponse> refresh(
      @CookieValue(name = "${auth.refresh-token.cookie-name}", required = false)
          @Nullable String refreshCookie,
      HttpServletResponse response) {
    if (refreshCookie == null || refreshCookie.isBlank()) {
      throw new UnauthorizedException("MISSING_REFRESH", "Refresh token cookie missing");
    }
    RotationResult result = refreshTokenService.rotate(refreshCookie);
    String access = authService.issueAccessTokenFor(result.userId());
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString());
    return ResponseEntity.ok(new RefreshResponse(access));
  }

  /**
   * Logout is intentionally {@code permitAll} so the client can clear its session even after the
   * access token expires. Possession of the refresh cookie is treated as the credential — anyone
   * who can present the cookie may revoke that specific session row. The cookie itself is locked
   * down by {@code HttpOnly + SameSite=Strict + Path=/api/auth/refresh}, so the realistic attack
   * surface is "someone who already has the cookie" (i.e. the legitimate user or an attacker who
   * has already compromised them). Other devices in different families are unaffected.
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "${auth.refresh-token.cookie-name}", required = false)
          @Nullable String refreshCookie,
      HttpServletResponse response) {
    if (refreshCookie != null && !refreshCookie.isBlank()) {
      refreshTokenService.revoke(refreshCookie);
    }
    response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString());
    return ResponseEntity.noContent().build();
  }
}
