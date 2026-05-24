package com.example.photomanagement.auth.dto;

/** Response body for token refresh. The rotated refresh token rides on a Set-Cookie header. */
public record RefreshResponse(String accessToken) {}
