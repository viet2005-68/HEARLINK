package com.example.heartlink.recommendation;

import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.recommendation.dto.RecommendationResponse;
import com.example.heartlink.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {
    private final UserRepository userRepository;

    public RecommendationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse list(Long userId, int limit) {
        if (limit < 1 || limit > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "limit must be between 1 and 50");
        }
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User does not exist");
        }
        var profiles = userRepository.findRecommendations(userId, PageRequest.of(0, limit))
                .stream().map(RecommendationResponse.Profile::from).toList();
        return new RecommendationResponse(profiles);
    }
}
