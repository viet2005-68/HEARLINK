package com.example.heartlink;

import com.example.heartlink.common.error.ApiException;
import com.example.heartlink.common.security.JwtAuthFilter;
import com.example.heartlink.common.security.JwtService;
import com.example.heartlink.entity.User;
import com.example.heartlink.repository.UserRepository;
import com.example.heartlink.swipe.SwipeService;
import com.example.heartlink.swipe.dto.SwipeRequest;
import com.example.heartlink.swipe.dto.SwipeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in: uses only the disposable PostgreSQL instance in docker-compose.test.yml. */
@EnabledIfEnvironmentVariable(named = "HEARTLINK_DB_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/heartlink_test",
        "spring.datasource.username=postgres", "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=phase1-test-secret-at-least-32-bytes-long"
})
class Phase1ApiTests {
    @Autowired WebApplicationContext context;
    @Autowired JwtAuthFilter filter;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired SwipeService swipes;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    Long a;
    Long b;
    Long c;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from matches");
        jdbc.update("delete from swipes");
        jdbc.update("delete from users");
        a = user("a");
        b = user("b");
        c = user("c");
    }

    private Long user(String name) {
        return users.saveAndFlush(User.builder().email(name + "@example.com")
                .name(name).bio("bio").passwordHash("unused-in-token-auth-tests").build()).getId();
    }

    private String auth(Long id) { return "Bearer " + jwt.issue(id); }

    @Test
    void recommendationsExcludeSelfAndBothActionsButKeepIncomingLikes() throws Exception {
        swipes.swipe(b, new SwipeRequest(a, "like"));
        mvc.perform(get("/api/v1/recommendations").header("Authorization", auth(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(b.intValue()))
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        swipes.swipe(a, new SwipeRequest(b, "like"));
        swipes.swipe(a, new SwipeRequest(c, "pass"));
        mvc.perform(get("/api/v1/recommendations").header("Authorization", auth(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void recommendationsEnforceDefaultAndLimit() throws Exception {
        for (int i = 0; i < 55; i++) user("extra" + i);
        mvc.perform(get("/api/v1/recommendations").header("Authorization", auth(a)))
                .andExpect(jsonPath("$.items.length()").value(20));
        mvc.perform(get("/api/v1/recommendations?limit=50").header("Authorization", auth(a)))
                .andExpect(jsonPath("$.items.length()").value(50));
        for (String limit : new String[]{"0", "-1", "51", "abc"}) {
            mvc.perform(get("/api/v1/recommendations?limit=" + limit).header("Authorization", auth(a)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").exists());
        }
    }

    @Test
    void bothEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/recommendations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/swipes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"target_id\":" + b + ",\"action\":\"like\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/recommendations").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesRequestAndBusinessErrors() throws Exception {
        for (String body : new String[]{"{}", "null", "{", "{\"target_id\":0,\"action\":\"like\"}",
                "{\"target_id\":" + b + ",\"action\":\"LIKE\"}",
                "{\"target_id\":" + b + ",\"action\":\"undo\"}",
                "{\"target_id\":" + a + ",\"action\":\"like\"}"}) {
            mvc.perform(post("/api/v1/swipes").header("Authorization", auth(a))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").exists());
        }
        mvc.perform(post("/api/v1/swipes").header("Authorization", auth(a))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"target_id\":9223372036854775807,\"action\":\"like\"}"))
                .andExpect(status().isNotFound());
        assertThat(count("swipes")).isZero();
    }

    @Test
    void likeCreatesOneCanonicalMatchAndCancelDoesNotAllowReswipe() throws Exception {
        mvc.perform(post("/api/v1/swipes").header("Authorization", auth(b))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"target_id\":" + a + ",\"action\":\"like\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.swipe_id").isNumber())
                .andExpect(jsonPath("$.matched").value(false)).andExpect(content().string(org.hamcrest.Matchers.containsString("\"match\":null")));
        mvc.perform(post("/api/v1/swipes").header("Authorization", auth(a))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"target_id\":" + b + ",\"action\":\"like\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.match.with_user_id").value(b.intValue()));
        assertThat(count("matches")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select user_a_id from matches", Long.class)).isEqualTo(a);
        Long matchId = jdbc.queryForObject("select id from matches", Long.class);
        mvc.perform(delete("/api/v1/matches/" + matchId).header("Authorization", auth(a)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/swipes").header("Authorization", auth(a))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"target_id\":" + b + ",\"action\":\"pass\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_SWIPED"));
        assertThat(count("swipes")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from matches", String.class)).isEqualTo("CANCELLED");
    }

    @Test
    void passDoesNotMatchIncomingLike() {
        swipes.swipe(a, new SwipeRequest(b, "like"));
        assertThat(swipes.swipe(b, new SwipeRequest(a, "pass")).matched()).isFalse();
        assertThat(count("matches")).isZero();
    }

    @Test
    void concurrentReciprocalLikesCreateExactlyOneMatch() throws Exception {
        var gate = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return swipes.swipe(a, new SwipeRequest(b, "like")); });
            var second = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return swipes.swipe(b, new SwipeRequest(a, "like")); });
            SwipeResponse x = first.get(15, TimeUnit.SECONDS);
            SwipeResponse y = second.get(15, TimeUnit.SECONDS);
            assertThat(x.matched() ^ y.matched()).isTrue();
        }
        assertThat(count("swipes")).isEqualTo(2);
        assertThat(count("matches")).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateSwipesReturnCreatedAndConflict() throws Exception {
        var gate = new CyclicBarrier(2);
        java.util.concurrent.Callable<Integer> call = () -> {
            gate.await(5, TimeUnit.SECONDS);
            try { swipes.swipe(a, new SwipeRequest(b, "like")); return 201; }
            catch (ApiException e) { return e.getStatus().value(); }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(call);
            var second = pool.submit(call);
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(count("swipes")).isEqualTo(1);
    }

    @Test
    void failedMatchInsertRollsBackNewSwipe() {
        swipes.swipe(b, new SwipeRequest(a, "like"));
        jdbc.execute("alter table matches add constraint test_reject_match check (id < 0)");
        try {
            assertThatThrownBy(() -> swipes.swipe(a, new SwipeRequest(b, "like")))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(count("swipes")).isEqualTo(1);
            assertThat(count("matches")).isZero();
        } finally {
            jdbc.execute("alter table matches drop constraint test_reject_match");
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
