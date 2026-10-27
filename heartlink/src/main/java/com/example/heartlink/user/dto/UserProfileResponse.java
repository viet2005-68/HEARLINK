package com.example.heartlink.user.dto;

import com.example.heartlink.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Read-only view of the current user's own profile. */
public record UserProfileResponse(Long id, String email, String name, String bio,
                                  @JsonProperty("created_at") Instant createdAt) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getName(), user.getBio(), user.getCreatedAt());
    }
}