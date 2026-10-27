package com.example.heartlink.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(name = "uq_notification_user_match_type",
                columnNames = {"user_id", "match_id", "type"}),
        indexes = @Index(name = "idx_notifications_user_id", columnList = "user_id,id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
