package com.vippro.heartlink.auth;

import com.vippro.heartlink.auth.dto.LoginRequest;
import com.vippro.heartlink.auth.dto.RegisterRequest;
import com.vippro.heartlink.auth.dto.RegisterResponse;
import com.vippro.heartlink.auth.dto.TokenResponse;
import com.vippro.heartlink.common.error.ApiException;
import com.vippro.heartlink.common.security.JwtProperties;
import com.vippro.heartlink.common.security.JwtService;
import com.vippro.heartlink.entity.RefreshToken;
import com.vippro.heartlink.entity.User;
import com.vippro.heartlink.repository.RefreshTokenRepository;
import com.vippro.heartlink.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Access tokens are stateless JWTs that expire on their own after expires_in.
 * Refresh tokens are opaque random strings stored as SHA-256 hashes; refresh rotates them and logout revokes them.
 */
@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshExpiresIn;
    private final Clock clock;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       JwtProperties jwtProperties, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshExpiresIn = jwtProperties.refreshExpiresIn();
        this.clock = clock;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }
        var user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name().trim())
                .bio(request.bio())
                .build();
        try {
            return RegisterResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // Lost a race with a concurrent registration of the same email.
            throw emailTaken();
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        // Same error for unknown email and wrong password, so emails cannot be enumerated.
        var user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Email or password is incorrect"));
        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        String hash = hash(refreshToken);
        Long userId = refreshTokenRepository.findUserIdByTokenHash(hash).orElse(null);
        // The conditional update makes rotation single-use even under concurrent refresh calls.
        if (userId == null || refreshTokenRepository.revokeIfActive(hash, clock.instant()) == 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                    "Refresh token is invalid, expired or revoked");
        }
        return issueTokens(userId);
    }

    /** Idempotent: an unknown or already revoked token is not an error. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.revokeIfActive(hash(refreshToken), clock.instant());
    }

    private TokenResponse issueTokens(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(userRepository.getReferenceById(userId))
                .tokenHash(hash(refreshToken))
                .expiresAt(clock.instant().plusSeconds(refreshExpiresIn))
                .build());
        return TokenResponse.bearer(jwtService.issue(userId), jwtService.getExpiresIn(), refreshToken);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
