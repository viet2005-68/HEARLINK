package com.example.heartlink.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Shared authentication for every /api/v1/** endpoint except /api/v1/auth/**.
 * Requires "Authorization: Bearer &lt;jwt&gt;" and exposes the user id as {@link CurrentUser#ID}.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final String PROTECTED_PREFIX = "/api/v1/";
    private static final String PUBLIC_PREFIX = "/api/v1/auth/";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith(PROTECTED_PREFIX) || path.startsWith(PUBLIC_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        Optional<JwtService.VerifiedToken> token = header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())
                ? jwtService.verifyToken(header.substring(BEARER.length()).trim())
                : Optional.empty();
        if (token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"error\":{\"code\":\"UNAUTHENTICATED\",\"message\":\"Missing or invalid access token\"}}");
            return;
        }
        request.setAttribute(CurrentUser.ID, token.get().userId());
        request.setAttribute(CurrentUser.EXPIRES_AT, token.get().expiresAt());
        chain.doFilter(request, response);
    }
}
