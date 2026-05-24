package com.example.photomanagement.auth;

import com.example.photomanagement.auth.dto.LoginRequest;
import com.example.photomanagement.auth.dto.LoginResponse;
import com.example.photomanagement.auth.dto.RefreshResponse;
import com.example.photomanagement.auth.dto.SignupRequest;
import com.example.photomanagement.common.error.UnauthorizedException;
import com.example.photomanagement.user.RoleMapper;
import com.example.photomanagement.user.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
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
  private final JwtService jwtService;
  private final RoleMapper roleMapper;
  private final RefreshTokenCookieFactory cookieFactory;

  public AuthController(
      AuthService authService,
      RefreshTokenService refreshTokenService,
      JwtService jwtService,
      RoleMapper roleMapper,
      RefreshTokenCookieFactory cookieFactory) {
    this.authService = authService;
    this.refreshTokenService = refreshTokenService;
    this.jwtService = jwtService;
    this.roleMapper = roleMapper;
    this.cookieFactory = cookieFactory;
  }

  @PostMapping("/signup")
  public ResponseEntity<LoginResponse> signup(
      @Valid @RequestBody SignupRequest req, HttpServletResponse response) {
    User user = authService.signup(req.email(), req.password());
    String access = issueAccessFor(user.id());
    NewRefreshToken refresh = refreshTokenService.issueForLogin(user.id());
    response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.create(refresh).toString());
    return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(access));
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest req, HttpServletResponse response) {
    User user = authService.authenticate(req.email(), req.password());
    String access = issueAccessFor(user.id());
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
    String access = issueAccessFor(result.userId());
    response.addHeader(
        HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString());
    return ResponseEntity.ok(new RefreshResponse(access));
  }

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

  private String issueAccessFor(Long userId) {
    List<String> roles = roleMapper.findRoleNamesByUserId(userId);
    return jwtService.issueAccessToken(userId, roles);
  }
}
