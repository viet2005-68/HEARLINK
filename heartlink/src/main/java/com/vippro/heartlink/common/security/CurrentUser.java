package com.vippro.heartlink.common.security;

/**
 * Request attribute set by {@link JwtAuthFilter} for authenticated requests.
 * Controllers read it with {@code @RequestAttribute(CurrentUser.ID) Long userId}.
 */
public final class CurrentUser {
    public static final String ID = "heartlink.currentUserId";

    private CurrentUser() { }
}
