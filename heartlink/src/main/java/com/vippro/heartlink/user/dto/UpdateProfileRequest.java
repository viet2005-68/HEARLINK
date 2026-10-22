package com.vippro.heartlink.user.dto;

import jakarta.validation.constraints.Size;

/** Partial update request for the current user's own profile (PATCH). Absent fields are left unchanged. */
public record UpdateProfileRequest(
        @Size(max = 100) String name,
        @Size(max = 500) String bio) {

    public boolean hasChanges() {
        return name != null || bio != null;
    }
}