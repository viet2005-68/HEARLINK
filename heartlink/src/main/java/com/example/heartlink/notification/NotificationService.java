package com.example.heartlink.notification;

import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.entity.Match;
import com.example.heartlink.entity.Notification;
import com.example.heartlink.entity.NotificationType;
import com.example.heartlink.notification.dto.NotificationPageResponse;
import com.example.heartlink.notification.dto.NotificationResponse;
import com.example.heartlink.repository.NotificationRepository;
import com.example.heartlink.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, UserRepository users,
                               ApplicationEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(Long userId, int limit, String cursor) {
        requireUser(userId);
        if (limit < 1 || limit > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "limit must be between 1 and 50");
        }
        Long beforeId = cursor == null ? null : decodeCursor(userId, cursor);
        var page = PageRequest.of(0, limit + 1);
        var rows = beforeId == null
                ? notifications.findByUserIdOrderByIdDesc(userId, page)
                : notifications.findByUserIdAndIdLessThanOrderByIdDesc(userId, beforeId, page);
        boolean hasMore = rows.size() > limit;
        var items = rows.stream().limit(limit).map(NotificationResponse::from).toList();
        String next = hasMore ? encodeCursor(userId, items.getLast().id()) : null;
        return new NotificationPageResponse(items, next);
    }

    @Transactional(readOnly = true)
    public void requireUser(Long userId) {
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User does not exist");
        }
    }

    /** Must join the transaction that persists the new match; never creates a separate transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMatchCreated(Match match) {
        var now = clock.instant();
        var rows = notifications.saveAllAndFlush(List.of(
                Notification.builder().user(match.getUserA()).match(match)
                        .type(NotificationType.MATCH_CREATED).createdAt(now).build(),
                Notification.builder().user(match.getUserB()).match(match)
                        .type(NotificationType.MATCH_CREATED).createdAt(now).build()));
        var deliveries = rows.stream().map(row -> new NotificationsCreated.Delivery(
                row.getUser().getId(), NotificationResponse.from(row))).toList();
        events.publishEvent(new NotificationsCreated(deliveries));
    }

    private static String encodeCursor(Long userId, Long lastId) {
        String value = "v1:" + userId + ":" + lastId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Long decodeCursor(Long userId, String cursor) {
        try {
            if (cursor.length() > 128) {
                throw new IllegalArgumentException();
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", -1);
            if (parts.length != 3 || !parts[0].equals("v1") || !Long.valueOf(parts[1]).equals(userId)) {
                throw new IllegalArgumentException();
            }
            long beforeId = Long.parseLong(parts[2]);
            if (beforeId < 1) {
                throw new IllegalArgumentException();
            }
            return beforeId;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Cursor is invalid for this user");
        }
    }
}
