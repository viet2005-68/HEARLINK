package com.example.heartlink.swipe;

import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.entity.Match;
import com.example.heartlink.entity.Swipe;
import com.example.heartlink.entity.SwipeAction;
import com.example.heartlink.entity.User;
import com.example.heartlink.match.MatchService;
import com.example.heartlink.repository.SwipeRepository;
import com.example.heartlink.repository.UserRepository;
import com.example.heartlink.swipe.dto.SwipeRequest;
import com.example.heartlink.swipe.dto.SwipeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SwipeService {
    private final UserRepository users;
    private final SwipeRepository swipes;
    private final MatchService matches;

    public SwipeService(UserRepository users, SwipeRepository swipes, MatchService matches) {
        this.users = users;
        this.swipes = swipes;
        this.matches = matches;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SwipeResponse swipe(Long userId, SwipeRequest request) {
        Long targetId = request.targetId();
        if (targetId == null || targetId <= 0
                || !("like".equals(request.action()) || "pass".equals(request.action()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid target_id or action");
        }
        if (userId.equals(targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_SWIPE", "Cannot swipe yourself");
        }

        // Serialize swipes involving either user. Ascending order avoids lock-order deadlocks.
        // READ_COMMITTED lets the reverse-like query see the transaction that just released the lock.
        User first = lockUser(Math.min(userId, targetId), userId);
        User second = lockUser(Math.max(userId, targetId), userId);
        User swiper = first.getId().equals(userId) ? first : second;
        User target = first.getId().equals(targetId) ? first : second;

        if (swipes.existsBySwiperIdAndTargetId(userId, targetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SWIPED", "You have already swiped this user");
        }
        SwipeAction action = "like".equals(request.action()) ? SwipeAction.LIKE : SwipeAction.PASS;
        Swipe swipe = swipes.saveAndFlush(Swipe.builder().swiper(swiper).target(target).action(action).build());

        SwipeResponse.MatchInfo matchInfo = null;
        if (action == SwipeAction.LIKE
                && swipes.existsBySwiperIdAndTargetIdAndAction(targetId, userId, SwipeAction.LIKE)) {
            Match match = matches.createForMutualLike(first, second);
            matchInfo = new SwipeResponse.MatchInfo(match.getId(), targetId);
        }
        return new SwipeResponse(swipe.getId(), matchInfo != null, matchInfo);
    }

    private User lockUser(Long id, Long currentUserId) {
        return users.findByIdForUpdate(id).orElseThrow(() -> id.equals(currentUserId)
                ? new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User does not exist")
                : new ApiException(HttpStatus.NOT_FOUND, "TARGET_NOT_FOUND", "Target user does not exist"));
    }
}
