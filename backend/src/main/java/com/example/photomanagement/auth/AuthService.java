package com.example.photomanagement.auth;

import com.example.photomanagement.common.error.ConflictException;
import com.example.photomanagement.common.error.UnauthorizedException;
import com.example.photomanagement.user.RoleMapper;
import com.example.photomanagement.user.User;
import com.example.photomanagement.user.UserMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user account creation and credential verification. Token issuance is delegated to {@code
 * JwtService} and {@link RefreshTokenService}; this class deals only in {@link User}.
 */
@Service
public class AuthService {

  private static final String ROLE_USER = "USER";

  private final UserMapper userMapper;
  private final RoleMapper roleMapper;
  private final PasswordHasher passwordHasher;
  private final RefreshTokenService refreshTokenService;
  private final JwtService jwtService;

  public AuthService(
      UserMapper userMapper,
      RoleMapper roleMapper,
      PasswordHasher passwordHasher,
      RefreshTokenService refreshTokenService,
      JwtService jwtService) {
    this.userMapper = userMapper;
    this.roleMapper = roleMapper;
    this.passwordHasher = passwordHasher;
    this.refreshTokenService = refreshTokenService;
    this.jwtService = jwtService;
  }

  /**
   * Loads the user's roles and mints a fresh access token. Kept in the service layer so the
   * controller does not call {@link RoleMapper} directly (3-layer rule enforced by ArchUnit).
   */
  @Transactional(readOnly = true)
  public String issueAccessTokenFor(Long userId) {
    List<String> roles = roleMapper.findRoleNamesByUserId(userId);
    return jwtService.issueAccessToken(userId, roles);
  }

  @Transactional
  public User signup(String email, String plainPassword) {
    if (userMapper.findActiveByEmail(email).isPresent()) {
      throw new ConflictException("EMAIL_TAKEN", "Email is already in use");
    }
    String hashed = passwordHasher.hash(plainPassword);
    try {
      userMapper.insert(email, hashed);
    } catch (DuplicateKeyException e) {
      // A concurrent signup with the same email won the race; the partial unique index fired.
      throw new ConflictException("EMAIL_TAKEN", "Email is already in use");
    }
    User created = userMapper.findActiveByEmail(email).orElseThrow();
    // V2__create_auth.sql seeds 'USER'. Missing it indicates schema tampering or a broken
    // migration; fail fast with an IllegalStateException (the catch-all in
    // ProblemDetailsAdvice surfaces this to clients as a 500 ProblemDetails).
    Short userRoleId =
        roleMapper
            .findIdByName(ROLE_USER)
            .orElseThrow(() -> new IllegalStateException("Seed role '" + ROLE_USER + "' missing"));
    roleMapper.assignRole(created.id(), userRoleId);
    return created;
  }

  /**
   * Verifies an email/password pair. Always runs a password comparison even when the email is
   * unknown so that response times do not leak account existence.
   */
  @Transactional(readOnly = true)
  public User authenticate(String email, String plainPassword) {
    Optional<User> maybeUser = userMapper.findActiveByEmail(email);
    String hashToCompare = maybeUser.map(User::passwordHash).orElse(passwordHasher.dummyHash());
    boolean matches = passwordHasher.matches(plainPassword, hashToCompare);
    if (maybeUser.isEmpty() || !matches) {
      throw new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect");
    }
    return maybeUser.get();
  }

  /**
   * Changes the password after verifying the old one, then revokes every active refresh token for
   * the user so existing sessions (potentially on other devices, or held by an attacker) cannot
   * continue. Outstanding access tokens remain valid until their natural expiry (≤ 15 min) — that
   * window is the documented trade-off of stateless JWT access tokens.
   *
   * <p>Not wired to an HTTP endpoint yet: the {@code POST /api/users/me/password} controller lands
   * in the M2 step 8 PR. The shape (userId taken from the authenticated principal; old + new
   * password in a request DTO) is fixed here so the controller layer is a straight pass-through.
   */
  @Transactional
  public void changePassword(Long userId, String oldPlainPassword, String newPlainPassword) {
    User user =
        userMapper
            .findActiveById(userId)
            .orElseThrow(() -> new UnauthorizedException("USER_GONE", "User no longer exists"));
    if (!passwordHasher.matches(oldPlainPassword, user.passwordHash())) {
      throw new UnauthorizedException("INVALID_CREDENTIALS", "Current password is incorrect");
    }
    userMapper.updatePasswordHash(user.id(), passwordHasher.hash(newPlainPassword));
    refreshTokenService.revokeAllForUser(user.id());
  }
}
