package com.example.heartlink.notification;

import com.example.heartlink.common.error.ApiException;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Phase 1 broadcaster: live subscriptions belong to this application instance. */
@Component
@EnableScheduling
public class NotificationStream {
    private static final Logger log = LoggerFactory.getLogger(NotificationStream.class);
    private final ConcurrentHashMap<Long, Set<Connection>> connections = new ConcurrentHashMap<>();
    private final Clock clock;

    public NotificationStream(Clock clock) {
        this.clock = clock;
    }

    public SseEmitter subscribe(Long userId, Instant expiresAt) {
        long timeout = Duration.between(clock.instant(), expiresAt).toMillis();
        if (timeout <= 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Access token has expired");
        }
        var emitter = new SseEmitter(timeout);
        var connection = new Connection(userId, expiresAt, emitter);
        emitter.onCompletion(() -> remove(connection));
        emitter.onTimeout(() -> close(connection));
        emitter.onError(error -> remove(connection));
        connections.compute(userId, (id, current) -> {
            var set = current == null ? ConcurrentHashMap.<Connection>newKeySet() : current;
            set.add(connection);
            return set;
        });
        send(connection, SseEmitter.event().comment("connected").reconnectTime(3000));
        return emitter;
    }

    @TransactionalEventListener
    public void onNotificationsCreated(NotificationsCreated event) {
        for (var delivery : event.deliveries()) {
            var clients = connections.get(delivery.userId());
            if (clients == null) {
                continue;
            }
            var notification = delivery.notification();
            var payload = new MatchEvent(notification.id(), notification.matchId());
            for (var connection : clients) {
                send(connection, SseEmitter.event().id(notification.id().toString())
                        .name("match.created").data(payload));
            }
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        connections.values().forEach(clients -> clients.forEach(connection ->
                send(connection, SseEmitter.event().comment("heartbeat"))));
    }

    private void send(Connection connection, SseEmitter.SseEventBuilder event) {
        if (!clock.instant().isBefore(connection.expiresAt())) {
            close(connection);
            return;
        }
        try {
            connection.emitter().send(event);
        } catch (IOException | IllegalStateException exception) {
            // A disconnected client must not turn a committed match into a failed API response.
            log.debug("Closing notification stream for user {}", connection.userId(), exception);
            close(connection);
        } catch (RuntimeException exception) {
            log.warn("Notification delivery failed for user {}; history remains available",
                    connection.userId(), exception);
            close(connection);
        }
    }

    private void remove(Connection connection) {
        connections.computeIfPresent(connection.userId(), (id, clients) -> {
            clients.remove(connection);
            return clients.isEmpty() ? null : clients;
        });
    }

    private void close(Connection connection) {
        remove(connection);
        connection.emitter().complete();
    }

    @PreDestroy
    public void shutdown() {
        connections.values().forEach(clients -> clients.forEach(this::close));
    }

    /** Close async requests before the web server begins its graceful-shutdown wait. */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        shutdown();
    }

    private record Connection(Long userId, Instant expiresAt, SseEmitter emitter) { }

    private record MatchEvent(@JsonProperty("notification_id") Long notificationId,
                              @JsonProperty("match_id") Long matchId) { }
}
