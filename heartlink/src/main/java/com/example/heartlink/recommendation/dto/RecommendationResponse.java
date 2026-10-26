package com.example.heartlink.recommendation.dto;

import com.example.heartlink.entity.User;
import java.util.List;

public record RecommendationResponse(List<Profile> items) {
    public record Profile(Long id, String name, String bio) {
        public static Profile from(User user) {
            return new Profile(user.getId(), user.getName(), user.getBio());
        }
    }
}
