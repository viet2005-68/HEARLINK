package com.example.heartlink.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank @Email String email,
                           @NotBlank String password) {
    /** Trim the email before validation so surrounding whitespace is not rejected by {@code @Email}. */
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
