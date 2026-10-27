package com.example.heartlink.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Partial update request for the current user's own profile (PATCH).
 * <ul>
 *   <li>{@code name}: absent or null leaves it unchanged (it cannot be cleared).</li>
 *   <li>{@code bio}: absent leaves it unchanged, an explicit JSON {@code null} clears it,
 *       a string replaces it. The setter records that the field was present so the two
 *       cases can be told apart. Its max length is enforced in the service.</li>
 * </ul>
 */
public class UpdateProfileRequest {
    @Size(max = 100)
    private String name;
    private String bio;
    private boolean bioProvided;

    public UpdateProfileRequest() { }

    @JsonProperty("name")
    public String name() { return name; }

    @JsonProperty("name")
    public void setName(String name) { this.name = name; }

    @JsonProperty("bio")
    public String bio() { return bio; }

    @JsonProperty("bio")
    public void setBio(String bio) {
        this.bio = bio;
        this.bioProvided = true;
    }

    /** True when the JSON body contained a "bio" key, even with a null value. */
    public boolean bioProvided() { return bioProvided; }

    public boolean hasChanges() {
        return name != null || bioProvided;
    }
}
