package com.neoul.ex.domain.auth.controller;

import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.domain.auth.repository.RevokedAccessTokenRepository;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.testutil.CommonResponseAssertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private BeachRepository beaches;
    @Autowired private RevokedAccessTokenRepository revokedTokens;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JsonMapper mapper;
    @Value("${jwt.secret}") private String secret;
    private User user;
    private Beach beach;

    @BeforeEach
    void setUp() {
        beach = beaches.saveAndFlush(Beach.create("로그아웃 검증 해변"));
        String email = UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        user = users.saveAndFlush(User.createGuard(email, passwordEncoder.encode("Abcd1234!"), beach));
    }

    @AfterEach
    void cleanUp() {
        revokedTokens.deleteAll();
        users.deleteById(user.getId());
        beaches.deleteById(beach.getId());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void logoutRevokesCurrentTokenAndAllowsNewLogin(Role role) throws Exception {
        ReflectionTestUtils.setField(user, "role", role);
        users.saveAndFlush(user);
        String token = login();
        mvc.perform(get("/ships").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("LOGOUT_COMPLETED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
        var stored = revokedTokens.findById(jwtProvider.parseAccessToken(token).tokenHash()).orElseThrow();
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(token);
        assertThat(stored.getExpiresAt()).isEqualTo(jwtProvider.parseAccessToken(token).expiresAt());
        mvc.perform(get("/ships").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        assertUnauthorized(token, "TOKEN_REVOKED");
        String newToken = login();
        assertThat(newToken).isNotEqualTo(token);
        mvc.perform(get("/ships").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void logoutDoesNotRevokeAnotherSession() throws Exception {
        String first = login();
        String second = login();
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk());
        mvc.perform(get("/ships").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
    }

    @Test
    void requiresBearerAuthentication() throws Exception {
        mvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        for (String header : new String[]{"", "Basic credentials", "Bearer ", "Bearer broken.token"}) {
            mvc.perform(post("/auth/logout").header("Authorization", header))
                    .andExpect(status().isUnauthorized())
                    .andExpect(CommonResponseAssertions::assertCommonResponse)
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
        assertThat(revokedTokens.count()).isZero();
    }

    @Test
    void rejectsExpiredForgedAndUnknownUserTokens() throws Exception {
        assertUnauthorized(new JwtProvider(secret, -60_000).createAccessToken(user.getId(), Role.GUARD), "TOKEN_EXPIRED");
        assertUnauthorized(new JwtProvider("a-different-test-signing-key-at-least-thirty-two-bytes", 60_000)
                .createAccessToken(user.getId(), Role.GUARD), "INVALID_TOKEN");
        assertUnauthorized(jwtProvider.createAccessToken(Long.MAX_VALUE, Role.GUARD), "INVALID_TOKEN");
        assertThat(revokedTokens.count()).isZero();
    }

    @Test
    void concurrentLogoutDoesNotCreateDuplicateRevocations() throws Exception {
        String token = login();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> logout = () -> {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
            return mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                    .andExpect(CommonResponseAssertions::assertCommonResponse)
                    .andExpect(result -> {
                        if (result.getResponse().getStatus() == 401) {
                            header().string("WWW-Authenticate", "Bearer").match(result);
                        }
                    })
                    .andReturn().getResponse().getStatus();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(logout);
            var second = executor.submit(logout);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        }
        assertThat(revokedTokens.count()).isEqualTo(1);
        assertUnauthorized(token, "TOKEN_REVOKED");
    }

    private String login() throws Exception {
        var result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "email", user.getEmail(), "password", "Abcd1234!"))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray()).get("data").get("accessToken").asString();
    }

    private void assertUnauthorized(String token, String code) throws Exception {
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
