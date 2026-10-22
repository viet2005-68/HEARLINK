package com.vippro.heartlink.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTests {
    private final JwtService jwtService = new JwtService(
            new JwtProperties("test-secret-test-secret-test-secret-123", 3600, 604800), Clock.systemUTC());
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtService);

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void validTokenSetsCurrentUser() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/matches");
        request.addHeader("Authorization", "Bearer " + jwtService.issue(3L));
        var chain = new MockFilterChain();

        assertThat(run(request, chain).getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(CurrentUser.ID)).isEqualTo(3L);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void missingTokenReturns401Envelope() throws Exception {
        var chain = new MockFilterChain();
        var response = run(new MockHttpServletRequest("GET", "/api/v1/matches"), chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        var request = new MockHttpServletRequest("DELETE", "/api/v1/matches/1");
        request.addHeader("Authorization", "Bearer abc.def.ghi");
        assertThat(run(request, new MockFilterChain()).getStatus()).isEqualTo(401);
    }

    @Test
    void authAndNonApiPathsArePublic() throws Exception {
        for (String path : new String[] {"/api/v1/auth/login", "/swagger-ui/index.html", "/v3/api-docs"}) {
            var chain = new MockFilterChain();
            assertThat(run(new MockHttpServletRequest("POST", path), chain).getStatus()).isEqualTo(200);
            assertThat(chain.getRequest()).as(path).isNotNull();
        }
    }
}
