package com.example.heartlink.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> items,
                                       @JsonProperty("next_cursor") String nextCursor) {
}
