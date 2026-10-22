package com.vippro.heartlink.user;

import com.vippro.heartlink.common.security.CurrentUser;
import com.vippro.heartlink.user.dto.UpdateProfileRequest;
import com.vippro.heartlink.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profile endpoints for the authenticated user (own account only). */
@Validated
@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** GET /api/v1/users/me — view own profile (read-only). */
    @GetMapping("/me")
    @Operation(
            summary = "View own profile",
            description = "Returns the authenticated user's own profile (read-only)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content)
    })
    public UserProfileResponse getMyProfile(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId) {
        return userService.getProfile(userId);
    }

    /** PATCH /api/v1/users/me — edit own profile. Only provided fields are updated. */
    @PatchMapping("/me")
    @Operation(
            summary = "Update own profile",
            description = "Updates the authenticated user's own profile. Only provided fields are changed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (no changes, blank/too-long fields)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content)
    })
    public UserProfileResponse updateMyProfile(
            @Parameter(hidden = true) @RequestAttribute(CurrentUser.ID) Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(userId, request);
    }
}