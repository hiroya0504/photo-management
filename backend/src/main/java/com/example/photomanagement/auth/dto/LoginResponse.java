package com.example.photomanagement.auth.dto;

/** Response body for signup and login. The refresh token rides on a Set-Cookie header. */
public record LoginResponse(String accessToken) {}
