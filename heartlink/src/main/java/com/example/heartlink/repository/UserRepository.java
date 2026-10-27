package com.example.heartlink.repository;

import com.example.heartlink.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            where u.id <> :userId
              and not exists (select s.id from Swipe s
                              where s.swiper.id = :userId and s.target.id = u.id)
            order by u.id
            """)
    List<User> findRecommendations(@Param("userId") Long userId, Pageable pageable);

    /** Call in ascending ID order inside the swipe transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
