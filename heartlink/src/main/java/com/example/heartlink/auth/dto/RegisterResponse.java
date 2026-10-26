package com.example.heartlink.auth.dto;

import com.example.heartlink.entity.User;

public record RegisterResponse(Long id, String email, String name, String bio) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(user.getId(), user.getEmail(), user.getName(), user.getBio());
    }
}
