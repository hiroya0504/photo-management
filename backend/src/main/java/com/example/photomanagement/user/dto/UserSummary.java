package com.example.photomanagement.user.dto;

import java.time.Instant;

/** Compact view of a user for the admin listing ({@code GET /api/admin/users}). */
public record UserSummary(Long id, String email, Instant createdAt) {}
