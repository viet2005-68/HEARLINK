package com.vippro.heartlink.match.dto;

import com.vippro.heartlink.entity.Match;

import java.time.Instant;

public record MatchResponse(Long id,
                            MatchUserResponse user,
                            String status,
                            Instant createdAt) {
    public static MatchResponse from(Match match, Long currentUserId) {
        var otherUser = match.getUserA().getId().equals(currentUserId)
                ? match.getUserB()
                : match.getUserA();
        return new MatchResponse(
                match.getId(),
                new MatchUserResponse(otherUser.getId(), otherUser.getName(), otherUser.getBio()),
                match.getStatus().name(),
                match.getCreatedAt());
    }
}
