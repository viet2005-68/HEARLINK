package com.example.heartlink.notification.dto;

import com.example.heartlink.entity.Notification;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record NotificationResponse(Long id, String type,
                                   @JsonProperty("match_id") Long matchId,
                                   @JsonProperty("created_at") Instant createdAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType().name(),
                notification.getMatch().getId(), notification.getCreatedAt());
    }
}
