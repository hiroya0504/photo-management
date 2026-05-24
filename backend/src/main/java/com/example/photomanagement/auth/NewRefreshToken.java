package com.example.photomanagement.auth;

import java.time.Instant;

/**
 * A freshly issued refresh token. The plaintext is the value to set on the response Cookie; the
 * server keeps only its hash in the database.
 */
public record NewRefreshToken(String plaintext, Instant expiresAt) {}
