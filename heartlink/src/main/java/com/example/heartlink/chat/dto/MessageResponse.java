package com.example.heartlink.chat.dto;

import com.example.heartlink.entity.Message;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record MessageResponse(Long id, @JsonProperty("sender_id") Long senderId,
                              String content, @JsonProperty("created_at") Instant createdAt) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(message.getId(), message.getSender().getId(),
                message.getContent(), message.getCreatedAt());
    }
}
