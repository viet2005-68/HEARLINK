package com.example.heartlink.auth;

import com.example.heartlink.auth.dto.LoginRequest;
import com.example.heartlink.auth.dto.RegisterRequest;
import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.common.security.JwtProperties;
import com.example.heartlink.common.security.JwtService;
import com.example.heartlink.entity.RefreshToken;
import com.example.heartlink.entity.User;
import com.example.heartlink.repository.RefreshTokenRepository;
import com.example.heartlink.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    private static final JwtProperties PROPS =
            new JwtProperties("test-secret-test-secret-test-secret-123", 3600, 604800);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(PROPS, clock);
    private final AuthService authService =
            new AuthService(userRepository, refreshTokenRepository, encoder, jwtService, PROPS, clock);

    private void givenUser() {
        var user = User.builder().id(1L).email("anna@example.com")
                .passwordHash(encoder.encode("123456")).name("Anna").build();
        when(userRepository.findByEmail("anna@example.com")).thenReturn(Optional.of(user));
    }

    private static void assertApiError(Throwable thrown, HttpStatus status, String code) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ----- login -----

    @Test
    void loginReturnsBearerAccessTokenAndStoresHashedRefreshToken() {
        givenUser();
        var response = authService.login(new LoginRequest(" Anna@Example.com ", "123456"));

        assertThat(response.tokenType()).isEqualTo("bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(jwtService.verify(response.accessToken())).contains(1L);

        var saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(saved.getValue().getTokenHash()).hasSize(64).isNotEqualTo(response.refreshToken());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(604800));
    }

    @Test
    void loginWithWrongPasswordIs401() {
        givenUser();
        assertApiError(catchThrown(() -> authService.login(new LoginRequest("anna@example.com", "wrong"))),
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }

    @Test
    void loginWithUnknownEmailIs401() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        assertApiError(catchThrown(() -> authService.login(new LoginRequest("nobody@example.com", "123456"))),
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }

    // ----- register -----

    @Test
    void registerHashesPasswordAndNormalizesEmail() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9L);
            return user;
        });

        var response = authService.register(new RegisterRequest("New@Example.com", "secret1", " Ivy ", "hi"));

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.name()).isEqualTo("Ivy");
        var saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(encoder.matches("secret1", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void registerWithExistingEmailIs409() {
        when(userRepository.existsByEmail("anna@example.com")).thenReturn(true);
        assertApiError(catchThrown(() -> authService.register(
                        new RegisterRequest("anna@example.com", "123456", "Anna", null))),
                HttpStatus.CONFLICT, "EMAIL_TAKEN");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerLosingUniqueRaceIs409() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertApiError(catchThrown(() -> authService.register(
                        new RegisterRequest("anna@example.com", "123456", "Anna", null))),
                HttpStatus.CONFLICT, "EMAIL_TAKEN");
    }

    // ----- refresh / logout -----

    @Test
    void refreshRotatesActiveToken() {
        when(refreshTokenRepository.findUserIdByTokenHash(anyString())).thenReturn(Optional.of(1L));
        when(refreshTokenRepository.revokeIfActive(anyString(), eq(NOW))).thenReturn(1);

        var response = authService.refresh("old-token");

        assertThat(jwtService.verify(response.accessToken())).contains(1L);
        assertThat(response.refreshToken()).isNotEqualTo("old-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshWithUnknownTokenIs401() {
        when(refreshTokenRepository.findUserIdByTokenHash(anyString())).thenReturn(Optional.empty());
        assertApiError(catchThrown(() -> authService.refresh("nope")),
                HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshWithRevokedOrExpiredTokenIs401() {
        when(refreshTokenRepository.findUserIdByTokenHash(anyString())).thenReturn(Optional.of(1L));
        when(refreshTokenRepository.revokeIfActive(anyString(), eq(NOW))).thenReturn(0);
        assertApiError(catchThrown(() -> authService.refresh("used")),
                HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logoutRevokesTokenAndIsIdempotent() {
        authService.logout("any-token");
        verify(refreshTokenRepository).revokeIfActive(anyString(), eq(NOW));
    }

    private static Throwable catchThrown(Runnable action) {
        return org.assertj.core.api.Assertions.catchThrowable(action::run);
    }
}
