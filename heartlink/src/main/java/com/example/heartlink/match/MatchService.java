package com.example.heartlink.match;

import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.entity.MatchStatus;
import com.example.heartlink.match.dto.MatchPageResponse;
import com.example.heartlink.match.dto.MatchResponse;
import com.example.heartlink.repository.MatchRepository;
import com.example.heartlink.repository.UserRepository;
import com.example.heartlink.entity.Match;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public MatchService(MatchRepository matchRepository, UserRepository userRepository) {
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
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
            throw new ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND",
                    "Active match was not found for this user");
        }
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User does not exist");
        }
    }
}
