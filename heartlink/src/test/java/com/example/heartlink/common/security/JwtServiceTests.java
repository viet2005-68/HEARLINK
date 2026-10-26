package com.example.heartlink.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {
    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

    private JwtService service(String secret, Instant now) {
        return new JwtService(new JwtProperties(secret, 3600, 604800), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenVerifiesToUserId() {
        var jwt = service(SECRET, NOW);
        assertThat(jwt.verify(jwt.issue(7L))).contains(7L);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(SECRET, NOW).issue(7L);
        assertThat(service(SECRET, NOW.plusSeconds(3601)).verify(token)).isEmpty();
    }

    @Test
    void tokenSignedWithOtherKeyIsRejected() {
        String token = service("another-secret-another-secret-another-1", NOW).issue(7L);
        assertThat(service(SECRET, NOW).verify(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(service(SECRET, NOW).verify("not-a-jwt")).isEmpty();
        assertThat(service(SECRET, NOW).verify("")).isEmpty();
    }
}
