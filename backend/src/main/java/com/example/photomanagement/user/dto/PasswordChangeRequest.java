package com.example.photomanagement.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/users/me/password}. The old password is required so a stolen access
 * token alone cannot change the credential; the new password follows the same rule as signup.
 */
public record PasswordChangeRequest(
    @NotBlank String oldPassword, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
