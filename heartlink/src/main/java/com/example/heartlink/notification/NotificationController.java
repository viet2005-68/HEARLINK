package com.example.heartlink.notification;

import com.example.heartlink.common.security.CurrentUser;
import com.example.heartlink.notification.dto.NotificationPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
    private final NotificationService notifications;
    private final NotificationStream stream;

    public NotificationController(NotificationService notifications, NotificationStream stream) {
        this.notifications = notifications;
        this.stream = stream;
    }

    @GetMapping
    @Operation(summary = "Notification history", description = "Own notifications, newest ID first. "
            + "Use next_cursor for older pages; omit cursor to refresh the latest page after reconnecting.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification page"),
            @ApiResponse(responseCode = "400", description = "Invalid limit or cursor", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing/invalid token or user", content = @Content)
    })
    public NotificationPageResponse list(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return notifications.list(userId, limit, cursor);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live notification stream", description = "Authenticated SSE. Event name: match.created; "
            + "event ID: notification ID; data: {notification_id, match_id}. Ends when the JWT expires. "
            + "Live events only; use history after reconnecting. Multiple devices are supported.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE connection"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid token or user", content = @Content)
    })
    public ResponseEntity<SseEmitter> subscribe(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId,
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.EXPIRES_AT) Instant expiresAt) {
        notifications.requireUser(userId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(stream.subscribe(userId, expiresAt));
    }
}
