package com.vippro.heartlink.repository;

import com.vippro.heartlink.entity.Match;
import com.vippro.heartlink.entity.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Match m
               set m.status = :status, m.cancelledAt = CURRENT_TIMESTAMP
             where m.id = :matchId
               and m.status = :activeStatus
               and (m.userA.id = :userId or m.userB.id = :userId)
            """)
    int cancelForUser(@Param("matchId") Long matchId,
                      @Param("userId") Long userId,
                      @Param("activeStatus") MatchStatus activeStatus,
                      @Param("status") MatchStatus status);
}
