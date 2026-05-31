package com.example.photomanagement.auth;

import com.example.photomanagement.common.error.ConflictException;
import com.example.photomanagement.common.error.UnauthorizedException;
import com.example.photomanagement.user.PasswordChanger;
import com.example.photomanagement.user.RoleMapper;
import com.example.photomanagement.user.User;
import com.example.photomanagement.user.UserMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles user account creation and credential verification. Token issuance is delegated to {@link
 * JwtService} and {@link RefreshTokenService}; this class deals only in {@link User}.
 *
 * <p>BCrypt (strength 12, ~100-300 ms per call) is run <strong>outside</strong> any JDBC
 * transaction so a single login does not pin a HikariCP connection for the full hashing time.
 * Programmatic {@link TransactionTemplate} is used for the multi-statement write paths so we can
 * scope the transaction tightly to the DB operations themselves.
 */
@Service
public class AuthService implements PasswordChanger {

  private static final String ROLE_USER = "USER";

  private final UserMapper userMapper;
  private final RoleMapper roleMapper;
  private final PasswordHasher passwordHasher;
  private final RefreshTokenService refreshTokenService;
  private final JwtService jwtService;
  private final TransactionTemplate writeTx;

  public AuthService(
      UserMapper userMapper,
      RoleMapper roleMapper,
      PasswordHasher passwordHasher,
      RefreshTokenService refreshTokenService,
      JwtService jwtService,
      PlatformTransactionManager txManager) {
    this.userMapper = userMapper;
    this.roleMapper = roleMapper;
    this.passwordHasher = passwordHasher;
    this.refreshTokenService = refreshTokenService;
    this.jwtService = jwtService;
    this.writeTx = new TransactionTemplate(txManager);
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

  public User signup(String email, String plainPassword) {
    // BCrypt runs outside any DB transaction.
    String hashed = passwordHasher.hash(plainPassword);
    return writeTx.execute(
        status -> {
          if (userMapper.findActiveByEmail(email).isPresent()) {
            throw new ConflictException("EMAIL_TAKEN", "Email is already in use");
          }
          try {
            userMapper.insert(email, hashed);
          } catch (DuplicateKeyException e) {
            // Concurrent signup with the same email won the race; partial unique index fired.
            throw new ConflictException("EMAIL_TAKEN", "Email is already in use");
          }
          User created = userMapper.findActiveByEmail(email).orElseThrow();
          // V2__create_auth.sql seeds 'USER'. Missing it indicates schema tampering or a broken
          // migration; fail fast with an IllegalStateException (the catch-all in
          // ProblemDetailsAdvice surfaces this to clients as a 500 ProblemDetails).
          Short userRoleId =
              roleMapper
                  .findIdByName(ROLE_USER)
                  .orElseThrow(
                      () -> new IllegalStateException("Seed role '" + ROLE_USER + "' missing"));
          roleMapper.assignRole(created.id(), userRoleId);
          return created;
        });
  }

  /**
   * Verifies an email/password pair. Always runs a password comparison even when the email is
   * unknown so that response times do not leak account existence.
   *
   * <p>No {@code @Transactional}: this is a single SELECT (no atomicity guarantee needed) followed
   * by CPU-bound BCrypt verification. Wrapping the BCrypt call in a transaction would needlessly
   * hold a JDBC connection for ~300 ms.
   */
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
   * <p>Exposed to the {@code user} feature via the {@link PasswordChanger} port (implemented here)
   * so {@code UserController} can serve {@code POST /api/users/me/password} without {@code user}
   * depending on {@code auth} — see ADR 0005. The BCrypt machinery stays inside this feature.
   *
   * <p>BCrypt verify + hash run outside of any JDBC transaction; only the {@code
   * updatePasswordHash} + {@code revokeAllForUser} writes are wrapped in one.
   */
  @Override
  public void changePassword(Long userId, String oldPlainPassword, String newPlainPassword) {
    User user =
        userMapper
            .findActiveById(userId)
            .orElseThrow(() -> new UnauthorizedException("USER_GONE", "User no longer exists"));
    if (!passwordHasher.matches(oldPlainPassword, user.passwordHash())) {
      throw new UnauthorizedException("INVALID_CREDENTIALS", "Current password is incorrect");
    }
    String newHashed = passwordHasher.hash(newPlainPassword);
    writeTx.executeWithoutResult(
        status -> {
          userMapper.updatePasswordHash(user.id(), newHashed);
          refreshTokenService.revokeAllForUser(user.id());
        });
  }
}
