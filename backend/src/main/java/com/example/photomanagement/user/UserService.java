package com.example.photomanagement.user;

import com.example.photomanagement.common.error.ConflictException;
import com.example.photomanagement.common.error.NotFoundException;
import com.example.photomanagement.common.error.UnauthorizedException;
import com.example.photomanagement.user.dto.UserResponse;
import com.example.photomanagement.user.dto.UserSummary;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service profile operations for the authenticated user. Password changes and session
 * revocation are delegated to the {@code auth} feature through the {@link PasswordChanger} and
 * {@link SessionRevoker} ports so this feature does not depend on {@code auth} (see ADR 0005).
 */
@Service
public class UserService {

  private final UserMapper userMapper;
  private final RoleMapper roleMapper;
  private final PasswordChanger passwordChanger;
  private final SessionRevoker sessionRevoker;

  public UserService(
      UserMapper userMapper,
      RoleMapper roleMapper,
      PasswordChanger passwordChanger,
      SessionRevoker sessionRevoker) {
    this.userMapper = userMapper;
    this.roleMapper = roleMapper;
    this.passwordChanger = passwordChanger;
    this.sessionRevoker = sessionRevoker;
  }

  @Transactional(readOnly = true)
  public UserResponse getProfile(Long userId) {
    User user = requireActive(userId);
    return toResponse(user);
  }

  /**
   * Updates the user's email. A collision with another active user's address surfaces as a 409 via
   * the partial unique index (caught as {@link DuplicateKeyException}).
   */
  @Transactional
  public UserResponse updateEmail(Long userId, String newEmail) {
    int updated;
    try {
      updated = userMapper.updateEmail(userId, newEmail);
    } catch (DuplicateKeyException e) {
      throw new ConflictException("EMAIL_TAKEN", "Email is already in use");
    }
    if (updated == 0) {
      throw new UnauthorizedException("USER_GONE", "User no longer exists");
    }
    return toResponse(requireActive(userId));
  }

  /**
   * Delegates to the {@code auth} feature; verifies the old password and revokes sessions there.
   */
  public void changePassword(Long userId, String oldPassword, String newPassword) {
    passwordChanger.changePassword(userId, oldPassword, newPassword);
  }

  /**
   * Soft-deletes the account and revokes every active refresh token so no session outlives the
   * deletion. The email becomes reusable immediately (the unique index is scoped to active rows).
   * Outstanding access tokens remain valid until their short expiry — the documented stateless-JWT
   * trade-off. The browser's refresh cookie is not cleared here (that would couple {@code user} to
   * {@code auth}'s cookie factory); it is already useless once the row is revoked.
   */
  @Transactional
  public void deleteAccount(Long userId) {
    int deleted = userMapper.softDelete(userId);
    if (deleted == 0) {
      throw new UnauthorizedException("USER_GONE", "User no longer exists");
    }
    sessionRevoker.revokeAllForUser(userId);
  }

  // -- admin operations (authorised at the edge by SecurityConfig: /api/admin/** -> hasRole ADMIN)

  /**
   * Lists active users for the admin console. Offset pagination for now; switching to the
   * cursor-based scheme used elsewhere in the app is tracked as a follow-up (see ADR 0005). {@code
   * limit} is clamped to a sane range so a caller cannot ask for an unbounded page.
   */
  @Transactional(readOnly = true)
  public List<UserSummary> listUsers(int limit, int offset) {
    int boundedLimit = Math.clamp(limit, 1, 100);
    int boundedOffset = Math.max(offset, 0);
    return userMapper.listActive(boundedLimit, boundedOffset).stream()
        .map(u -> new UserSummary(u.id(), u.email(), u.createdAt()))
        .toList();
  }

  /**
   * Admin soft-delete of an arbitrary user, revoking their sessions. A missing target is a 404
   * (unlike self-delete, where a missing row means the caller's own token is stale -> 401).
   */
  @Transactional
  public void deleteUserAsAdmin(Long targetUserId) {
    int deleted = userMapper.softDelete(targetUserId);
    if (deleted == 0) {
      throw new NotFoundException("User " + targetUserId + " not found");
    }
    sessionRevoker.revokeAllForUser(targetUserId);
  }

  private User requireActive(Long userId) {
    return userMapper
        .findActiveById(userId)
        .orElseThrow(() -> new UnauthorizedException("USER_GONE", "User no longer exists"));
  }

  private UserResponse toResponse(User user) {
    List<String> roles = roleMapper.findRoleNamesByUserId(user.id());
    return new UserResponse(user.id(), user.email(), roles, user.createdAt());
  }
}
