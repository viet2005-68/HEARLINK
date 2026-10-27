package com.example.heartlink.match;

import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.entity.MatchStatus;
import com.example.heartlink.match.dto.MatchPageResponse;
import com.example.heartlink.match.dto.MatchResponse;
import com.example.heartlink.repository.MatchRepository;
import com.example.heartlink.repository.UserRepository;
import com.example.heartlink.entity.Match;
import com.example.heartlink.entity.User;
import com.example.heartlink.notification.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    public MatchService(MatchRepository matchRepository, UserRepository userRepository,
                        NotificationService notificationService) {
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Internal write operation for the swipe workflow, after it establishes mutual likes.
     * The caller owns reciprocal-like validation/concurrency control; this operation stores
     * the canonical pair and both notifications atomically. There is no public create-match API.
     */
    @Transactional
    public Match createForMutualLike(User first, User second) {
        if (first.getId().equals(second.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_MATCH", "A user cannot match with themselves");
        }
        boolean ordered = first.getId() < second.getId();
        var match = matchRepository.saveAndFlush(Match.builder()
                .userA(ordered ? first : second)
                .userB(ordered ? second : first)
                .status(MatchStatus.ACTIVE)
                .build());
        notificationService.recordMatchCreated(match);
        return match;
    }

    @Transactional(readOnly = true)
    public MatchPageResponse listActive(Long userId, int limit, long offset) {
        requireUser(userId);
        if (offset > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OFFSET", "offset is too large");
        }
        var matches = entityManager.createQuery("""
                        select m from Match m
                        join fetch m.userA
                        join fetch m.userB
                        where (m.userA.id = :userId or m.userB.id = :userId)
                          and m.status = :status
                        order by m.createdAt desc, m.id desc
                        """, Match.class)
                .setParameter("userId", userId)
                .setParameter("status", MatchStatus.ACTIVE)
                .setFirstResult((int) offset)
                .setMaxResults(limit)
                .getResultList();
        long total = entityManager.createQuery("""
                        select count(m) from Match m
                        where (m.userA.id = :userId or m.userB.id = :userId)
                          and m.status = :status
                        """, Long.class)
                .setParameter("userId", userId)
                .setParameter("status", MatchStatus.ACTIVE)
                .getSingleResult();
        var items = matches.stream().map(match -> MatchResponse.from(match, userId)).toList();
        return new MatchPageResponse(items, limit, offset, total);
    }

    @Transactional
    public void cancel(Long userId, Long matchId) {
        requireUser(userId);
        int updated = matchRepository.cancelForUser(
                matchId, userId, MatchStatus.ACTIVE, MatchStatus.CANCELLED);
        if (updated == 0) {
            if (matchRepository.existsByIdAndStatus(matchId, MatchStatus.ACTIVE)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MATCH_FORBIDDEN",
                        "Only participants can cancel this match");
            }
            throw new ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND",
                    "Active match was not found");
        }
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User does not exist");
        }
    }
}
