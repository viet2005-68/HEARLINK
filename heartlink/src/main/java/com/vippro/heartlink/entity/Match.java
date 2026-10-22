package com.vippro.heartlink.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity: matches
 * <p>
 * id (PK), user_a_id, user_b_id, status (active/cancelled), created_at, cancelled_at
 */
@Entity
@Table(name = "matches",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_match_pair", columnNames = {"user_a_id", "user_b_id"})
        },
        indexes = {
                @Index(name = "idx_matches_user_a_status_created", columnList = "user_a_id,status,created_at"),
                @Index(name = "idx_matches_user_b_status_created", columnList = "user_b_id,status,created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id", nullable = false)
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id", nullable = false)
    private User userB;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private MatchStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = MatchStatus.ACTIVE;
        }
    }
}
