package com.example.photomanagement.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @Email @NotBlank @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 128) String password) {}
