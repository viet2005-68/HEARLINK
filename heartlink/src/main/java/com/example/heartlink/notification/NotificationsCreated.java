package com.example.heartlink.notification;

import com.example.heartlink.notification.dto.NotificationResponse;

import java.util.List;

/** Immutable payloads survive persistence-context cleanup at commit. */
public record NotificationsCreated(List<Delivery> deliveries) {
    public record Delivery(Long userId, NotificationResponse notification) { }
}
