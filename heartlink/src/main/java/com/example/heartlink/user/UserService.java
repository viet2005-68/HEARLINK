package com.example.heartlink.user;

import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.entity.User;
import com.example.heartlink.repository.UserRepository;
import com.example.heartlink.user.dto.UpdateProfileRequest;
import com.example.heartlink.user.dto.UserProfileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(requireUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        var user = requireUser(userId);
        if (!request.hasChanges()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_FIELDS_TO_UPDATE",
                    "At least one of name or bio must be provided");
        }
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Name must not be blank");
            }
            if (name.length() > 100) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Name must be at most 100 characters");
            }
            user.setName(name);
        }
        if (request.bioProvided()) {
            // An explicit JSON null clears the bio.
            String bio = request.bio();
            if (bio != null && bio.length() > 500) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Bio must be at most 500 characters");
            }
            user.setBio(bio);
        }
        return UserProfileResponse.from(userRepository.saveAndFlush(user));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND",
                        "User does not exist"));
    }
}