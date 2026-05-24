package com.example.photomanagement.user;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Application user. Mirrors the {@code users} table.
 *
 * <p>A soft-deleted user keeps the row with {@code deletedAt} set; the unique index on email is
 * scoped to active rows so the address can be reused after deletion.
 */
public record User(
    Long id,
    String email,
    String passwordHash,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant deletedAt) {}
