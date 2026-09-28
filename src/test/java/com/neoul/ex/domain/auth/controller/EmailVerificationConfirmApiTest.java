package com.neoul.ex.domain.auth.controller;

import com.neoul.ex.domain.auth.repository.EmailVerificationRepository;
import com.neoul.ex.domain.auth.service.EmailVerificationService;
import com.neoul.ex.domain.auth.service.VerificationMailSender;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.testutil.CommonResponseAssertions;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
class EmailVerificationConfirmApiTest {
    private static final String EMAIL = "confirm@example.com";
    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired EmailVerificationService service;

    @Autowired EmailVerificationRepository verifications;
    @Autowired JsonMapper mapper;
    @MockitoBean VerificationMailSender mail;
    @MockitoBean Clock clock;
    private String code;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        doAnswer(invocation -> { code = invocation.getArgument(1); return null; })
                .when(mail).sendCode(eq(EMAIL), anyString());
    }

    @AfterEach
    void cleanUp() { verifications.deleteAll(); }

    @Test
    void confirmsNormalizedEmailAndIssuesHashedSingleUseProof() throws Exception {
        service.send(EMAIL);
        var response = mvc.perform(post("/auth/email-verifications/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" Confirm@Example.com \",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFIED"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-09-21T00:10:00Z"))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(response).get("data").get("verificationToken").asString();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(verifications.findById(EMAIL).orElseThrow().getProofHash()).hasSize(64).isNotEqualTo(token);
        confirm(code, 400, "EMAIL_CODE_INVALID");
    }

    @Test
    void rejectsAtExactThreeMinuteExpiry() throws Exception {
        service.send(EMAIL);
        when(clock.instant()).thenReturn(NOW.plusSeconds(180));
        confirm(code, 400, "EMAIL_CODE_EXPIRED");
    }

    @Test
    void acceptsImmediatelyBeforeThreeMinuteExpiry() throws Exception {
        service.send(EMAIL);
        when(clock.instant()).thenReturn(NOW.plusSeconds(180).minusNanos(1));
        confirm(code, 200, "EMAIL_VERIFIED");
    }

    @Test
    void fifthWrongGuessLocksTheCodeAndResendResetsAttempts() throws Exception {
        service.send(EMAIL);
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 4; i++) confirm(wrong, 400, "EMAIL_CODE_INVALID");
        confirm(wrong, 429, "EMAIL_CODE_ATTEMPTS_EXCEEDED");
        confirm(code, 429, "EMAIL_CODE_ATTEMPTS_EXCEEDED");
        assertThat(verifications.findById(EMAIL).orElseThrow().getFailedAttempts()).isEqualTo(5);
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));
        service.send(EMAIL);
        confirm(code, 200, "EMAIL_VERIFIED");
    }

    @Test
    void resendInvalidatesOldCodeAndExistingProof() throws Exception {
        service.send(EMAIL);
        String oldCode = code;
        var first = service.confirm(EMAIL, code);
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));
        service.send(EMAIL);
        assertThat(verifications.findById(EMAIL).orElseThrow().getProofHash()).isNull();
        confirm(oldCode, 400, "EMAIL_CODE_INVALID");
        var second = service.confirm(EMAIL, code);
        assertThat(second.verificationToken()).isNotEqualTo(first.verificationToken());
    }

    @Test
    void rejectsMissingIssueAndInvalidCodeFormats() throws Exception {
        confirm("123456", 400, "EMAIL_CODE_INVALID");
        for (String bad : List.of("", "12345", "1234567", "abcdef", "１２３４５６", " 12345")) {
            confirm(bad, 400, "INVALID_INPUT");
        }
    }

    @Test
    void simultaneousConfirmationIssuesOnlyOneProof() throws Exception {
        service.send(EMAIL);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<String> confirm = () -> {
                start.await();
                try { service.confirm(EMAIL, code); return "VERIFIED"; }
                catch (BusinessException error) { return error.getErrorCode().name(); }
            };
            var first = executor.submit(confirm);
            var second = executor.submit(confirm);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("VERIFIED", "EMAIL_CODE_INVALID");
        }
    }

    private void confirm(String value, int status, String expectedCode) throws Exception {
        mvc.perform(post("/auth/email-verifications/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"confirm@example.com\",\"code\":\"" + value + "\"}"))
                .andExpect(status().is(status)).andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value(expectedCode));
    }
}
