package com.example.photomanagement.auth;

import com.example.photomanagement.common.error.ConflictException;
import com.example.photomanagement.common.error.UnauthorizedException;
import com.example.photomanagement.user.RoleMapper;
import com.example.photomanagement.user.User;
import com.example.photomanagement.user.UserMapper;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user account creation and credential verification. Token issuance is delegated to {@code
 * JwtService} and {@code RefreshTokenService}; this class deals only in {@link User}.
 */
@Service
public class AuthService {

  private static final String ROLE_USER = "USER";

  private final UserMapper userMapper;
  private final RoleMapper roleMapper;
  private final PasswordHasher passwordHasher;

  public AuthService(UserMapper userMapper, RoleMapper roleMapper, PasswordHasher passwordHasher) {
    this.userMapper = userMapper;
    this.roleMapper = roleMapper;
    this.passwordHasher = passwordHasher;
  }

  @Transactional
  public User signup(String email, String plainPassword) {
    if (userMapper.findActiveByEmail(email).isPresent()) {
      throw new ConflictException("EMAIL_TAKEN", "Email is already in use");
    }
    String hashed = passwordHasher.hash(plainPassword);
    userMapper.insert(email, hashed);
    User created = userMapper.findActiveByEmail(email).orElseThrow();
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
  }
}
