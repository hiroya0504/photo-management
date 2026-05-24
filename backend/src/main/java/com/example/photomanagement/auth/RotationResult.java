package com.example.photomanagement.auth;

/** Outcome of a successful refresh-token rotation. */
public record RotationResult(Long userId, NewRefreshToken refreshToken) {}
