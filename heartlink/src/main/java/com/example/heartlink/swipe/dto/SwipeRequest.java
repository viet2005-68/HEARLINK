package com.example.heartlink.swipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record SwipeRequest(
        @JsonProperty("target_id") @NotNull @Positive Long targetId,
        @NotNull @Pattern(regexp = "like|pass") String action) {
}
