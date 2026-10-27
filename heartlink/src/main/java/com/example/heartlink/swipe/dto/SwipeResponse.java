package com.example.heartlink.swipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SwipeResponse(@JsonProperty("swipe_id") Long swipeId, boolean matched, MatchInfo match) {
    public record MatchInfo(Long id, @JsonProperty("with_user_id") Long withUserId) {}
}
