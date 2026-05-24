package com.example.photomanagement.auth;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Mirrors a row of the {@code refresh_tokens} table. The plaintext token is never stored; only its
 * SHA-256 digest lives in {@code tokenHash}.
 */
public record RefreshTokenRecord(
    Long id,
    Long userId,
    UUID familyId,
    String tokenHash,
    Instant expiresAt,
    @Nullable Instant usedAt,
    @Nullable Instant revokedAt,
    Instant createdAt) {}
