package com.example.photomanagement.user;

import com.example.photomanagement.common.web.AuthenticatedUserResolver;
import com.example.photomanagement.user.dto.PasswordChangeRequest;
import com.example.photomanagement.user.dto.ProfileUpdateRequest;
import com.example.photomanagement.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-profile endpoints for the authenticated user. All four require a valid Bearer JWT (enforced
 * by {@code SecurityConfig}'s {@code anyRequest().authenticated()}); the acting user id always
 * comes from the token via {@link AuthenticatedUserResolver}, never from the request body, so a
 * user can only ever read or mutate their own account.
 */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

  private final UserService userService;
  private final AuthenticatedUserResolver currentUser;

  public UserController(UserService userService, AuthenticatedUserResolver currentUser) {
    this.userService = userService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public UserResponse getMe() {
    return userService.getProfile(currentUser.currentUserId());
  }

  @PatchMapping
  public UserResponse updateMe(@Valid @RequestBody ProfileUpdateRequest req) {
    return userService.updateEmail(currentUser.currentUserId(), req.email());
  }

  @PostMapping("/password")
  public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest req) {
    userService.changePassword(currentUser.currentUserId(), req.oldPassword(), req.newPassword());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping
  public ResponseEntity<Void> deleteMe() {
    userService.deleteAccount(currentUser.currentUserId());
    return ResponseEntity.noContent().build();
  }
}
