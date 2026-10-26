package com.example.heartlink.repository;

import com.example.heartlink.entity.Swipe;
import com.example.heartlink.entity.SwipeAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SwipeRepository extends JpaRepository<Swipe, Long> {
    boolean existsBySwiperIdAndTargetId(Long swiperId, Long targetId);

    boolean existsBySwiperIdAndTargetIdAndAction(Long swiperId, Long targetId, SwipeAction action);
}
