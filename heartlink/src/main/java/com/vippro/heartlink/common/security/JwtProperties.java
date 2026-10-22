package com.vippro.heartlink.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.jwt.secret             - HMAC key, at least 32 bytes (HS256)
 * app.jwt.expires-in         - access token lifetime in seconds
 * app.jwt.refresh-expires-in - refresh token lifetime in seconds
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expiresIn, long refreshExpiresIn) {
}
