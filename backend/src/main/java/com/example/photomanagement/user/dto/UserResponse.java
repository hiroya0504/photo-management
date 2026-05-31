package com.example.photomanagement.user.dto;

import java.time.Instant;
import java.util.List;

/** Profile of the authenticated user. Returned by {@code GET} and {@code PATCH /api/users/me}. */
public record UserResponse(Long id, String email, List<String> roles, Instant createdAt) {}
