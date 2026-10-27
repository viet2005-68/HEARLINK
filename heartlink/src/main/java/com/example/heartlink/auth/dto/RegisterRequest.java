package com.example.heartlink.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** BCrypt only uses the first 72 bytes of a password, hence the upper bound. */
public record RegisterRequest(@NotBlank @Email @Size(max = 255) String email,
                              @NotBlank @Size(min = 6, max = 72) String password,
                              @NotBlank @Size(max = 100) String name,
                              @Size(max = 500) String bio) {
    /** Trim the email before validation so surrounding whitespace is not rejected by {@code @Email}. */
    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
