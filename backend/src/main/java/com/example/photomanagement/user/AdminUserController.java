package com.example.photomanagement.user;

import com.example.photomanagement.common.web.AuthenticatedUserResolver;
import com.example.photomanagement.user.dto.UserSummary;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only user management. Authorisation is enforced at the edge by {@code SecurityConfig}
 * ({@code /api/admin/**} requires {@code ROLE_ADMIN}), so these handlers carry no per-method
 * security annotation — the authz rule lives in one place. See ADR 0005.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final UserService userService;
  private final AuthenticatedUserResolver currentUser;

  public AdminUserController(UserService userService, AuthenticatedUserResolver currentUser) {
    this.userService = userService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public List<UserSummary> listUsers(
      @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
    return userService.listUsers(limit, offset);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userService.deleteUserAsAdmin(currentUser.currentUserId(), id);
    return ResponseEntity.noContent().build();
  }
}
