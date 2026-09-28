package com.neoul.ex.domain.auth.controller;

import com.neoul.ex.domain.auth.dto.SignupRequest;
import com.neoul.ex.domain.auth.repository.EmailVerificationRepository;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.domain.auth.service.AuthService;
import com.neoul.ex.domain.auth.service.EmailVerificationService;
import com.neoul.ex.domain.auth.service.VerificationMailSender;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.testutil.CommonResponseAssertions;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class VerifiedSignupApiTest {
    private static final String EMAIL = "verified@example.com";
    private static final String OTHER = "other@example.com";
    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired EmailVerificationService verificationService;
    @Autowired EmailVerificationRepository verifications;
    @Autowired UserRepository users;
    @Autowired BeachRepository beaches;
    @Autowired JsonMapper mapper;
    @MockitoBean VerificationMailSender mail;
    @MockitoBean Clock clock;
    private final Map<String, String> codes = new HashMap<>();
    private Beach beach;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        doAnswer(invocation -> { codes.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(mail).sendCode(anyString(), anyString());
        beach = beaches.saveAndFlush(Beach.create("인증 가입 테스트 해변"));
    }

    @AfterEach
    void cleanUp() {
        users.findByEmail(EMAIL).ifPresent(users::delete);
        users.findByEmail(OTHER).ifPresent(users::delete);
        verifications.deleteAll();
        beaches.deleteById(beach.getId());
    }

    @Test
    void completeHttpFlowCreatesGuardAndConsumesProof() throws Exception {
        String token = verifyEmail(EMAIL);
        when(clock.instant()).thenReturn(NOW.plusSeconds(181));
        signup(EMAIL, token, beach.getId(), 201, "SIGNUP_COMPLETED");
        assertThat(users.findByEmail(EMAIL).orElseThrow().getRole().name()).isEqualTo("GUARD");
        assertThat(users.findByEmail(EMAIL).orElseThrow().getName()).isEqualTo("홍길동");
        assertThat(verifications.findById(EMAIL).orElseThrow().getProofHash()).isNull();
        signup(EMAIL, token, beach.getId(), 409, "DUPLICATE_EMAIL");
        users.delete(users.findByEmail(EMAIL).orElseThrow());
        signup(EMAIL, token, beach.getId(), 403, "EMAIL_NOT_VERIFIED");
    }

    @Test
    void unverifiedEmailCannotSignupEvenWithPlausibleToken() throws Exception {
        signup(EMAIL, "a".repeat(43), beach.getId(), 403, "EMAIL_NOT_VERIFIED");
        assertThat(users.existsByEmail(EMAIL)).isFalse();
    }

    @Test
    void missingProofAndMalformedProofAreRejected() throws Exception {
        var body = new HashMap<String, Object>();
        body.put("email", EMAIL);
        body.put("name", "홍길동");
        body.put("password", "Password1!");
        body.put("passwordConfirm", "Password1!");
        body.put("beachId", beach.getId());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(CommonResponseAssertions::assertCommonResponse);
        signup(EMAIL, "short-token", beach.getId(), 400, "INVALID_INPUT");
    }

    @Test
    void rejectsAnotherEmailsProofAndForgedProofWithoutConsumingCorrectProof() throws Exception {
        String token = verifyEmail(EMAIL);
        signup(OTHER, token, beach.getId(), 403, "EMAIL_NOT_VERIFIED");
        String wrong = (token.charAt(0) == 'a' ? "b" : "a") + token.substring(1);
        signup(EMAIL, wrong, beach.getId(), 403, "EMAIL_NOT_VERIFIED");
        signup(EMAIL, token, beach.getId(), 201, "SIGNUP_COMPLETED");
    }

    @Test
    void failedSignupRollsBackProofConsumption() throws Exception {
        String token = verifyEmail(EMAIL);
        signup(EMAIL, token, Long.MAX_VALUE, 404, "BEACH_NOT_FOUND");
        assertThat(verifications.findById(EMAIL).orElseThrow().getProofHash()).isNotNull();
        signup(EMAIL, token, beach.getId(), 201, "SIGNUP_COMPLETED");
    }

    @Test
    void proofExpiresAtExactTenMinutes() throws Exception {
        String token = verifyEmail(EMAIL);
        when(clock.instant()).thenReturn(NOW.plusSeconds(600));
        signup(EMAIL, token, beach.getId(), 403, "EMAIL_NOT_VERIFIED");
        assertThat(users.existsByEmail(EMAIL)).isFalse();
    }

    @Test
    void successfulResendInvalidatesPreviousSignupProof() throws Exception {
        String token = verifyEmail(EMAIL);
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));
        verificationService.send(EMAIL);
        signup(EMAIL, token, beach.getId(), 403, "EMAIL_NOT_VERIFIED");
    }

    @Test
    void invalidNameDoesNotCreateUserOrConsumeVerificationProof() throws Exception {
        String token = verifyEmail(EMAIL);
        String proofHash = verifications.findById(EMAIL).orElseThrow().getProofHash();
        var body = new HashMap<String, Object>(Map.of("email", EMAIL, "password", "Password1!",
                "passwordConfirm", "Password1!", "beachId", beach.getId(), "verificationToken", token));
        for (String name : new String[]{null, "", " \t ", "가".repeat(51)}) {
            if (name == null) body.remove("name");
            else body.put("name", name);
            mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest()).andExpect(CommonResponseAssertions::assertCommonResponse)
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.data.fieldErrors[0].field").value("name"));
            assertThat(users.existsByEmail(EMAIL)).isFalse();
            assertThat(verifications.findById(EMAIL).orElseThrow().getProofHash()).isEqualTo(proofHash);
        }
        signup(EMAIL, token, beach.getId(), 201, "SIGNUP_COMPLETED");
        assertThat(users.findByEmail(EMAIL).orElseThrow().getName()).isEqualTo("홍길동");
    }

    @Test
    void simultaneousSignupCreatesOnlyOneAccount() throws Exception {
        String token = verifyEmail(EMAIL);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<String> signup = () -> {
                start.await();
                try {
                    auth.signup(new SignupRequest(EMAIL, "Password1!", "Password1!", beach.getId(), token, "홍길동"));
                    return "CREATED";
                } catch (BusinessException error) { return error.getErrorCode().name(); }
            };
            var first = executor.submit(signup);
            var second = executor.submit(signup);
            start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter("CREATED"::equals).count()).isEqualTo(1);
            assertThat(results).allMatch(value -> List.of("CREATED", "EMAIL_NOT_VERIFIED", "DUPLICATE_EMAIL").contains(value));
            assertThat(users.findByEmail(EMAIL)).isPresent();
        }
    }

    private String verifyEmail(String email) throws Exception {
        mvc.perform(post("/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
        var result = mvc.perform(post("/auth/email-verifications/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "code", codes.get(email)))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("verificationToken").asString();
    }

    private void signup(String email, String token, Long beachId, int expectedStatus, String expectedCode) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", "Password1!",
                                "passwordConfirm", "Password1!", "beachId", beachId, "verificationToken", token, "name", "  홍길동  "))))
                .andExpect(status().is(expectedStatus)).andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.data.verificationToken").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(result -> {
                    if (expectedStatus == 201) {
                        assertThat(mapper.readTree(result.getResponse().getContentAsString())
                                .get("data").get("name").asString()).isEqualTo("홍길동");
                    }
                });
    }
}
