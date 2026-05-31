package com.example.photomanagement.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for {@code PATCH /api/users/me}. Only the email is mutable for now. */
public record ProfileUpdateRequest(@Email @NotBlank @Size(max = 255) String email) {}
