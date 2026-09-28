package com.neoul.ex.domain.auth.controller;

import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.auth.repository.EmailVerificationRepository;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.domain.auth.service.EmailVerificationService;
import com.neoul.ex.domain.auth.service.VerificationMailSender;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.global.testutil.CommonResponseAssertions;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationSendApiTest {
    private static final String EMAIL = "verify@example.com";
    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired EmailVerificationService service;
    @Autowired EmailVerificationRepository verifications;
    @Autowired UserRepository users;
    @Autowired BeachRepository beaches;
    @Autowired PasswordEncoder encoder;
    @MockitoBean VerificationMailSender mail;
    @MockitoBean Clock clock;

    @BeforeEach
    void setUp() { when(clock.instant()).thenReturn(NOW); }

    @AfterEach
    void cleanUp() { verifications.deleteAll(); }

    @Test
    void sendsSixDigitsWithDeadlinesAndNoCodeInResponseOrStorage() throws Exception {
        mvc.perform(post("/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" Verify@Example.com \"}"))
                .andExpect(status().isOk()).andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_SENT"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-09-21T00:03:00Z"))
                .andExpect(jsonPath("$.data.resendAvailableAt").value("2026-09-21T00:00:30Z"))
                .andExpect(jsonPath("$.data.code").doesNotExist());
        var code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendCode(eq(EMAIL), code.capture());
        assertThat(code.getValue()).matches("[0-9]{6}");
        var saved = verifications.findById(EMAIL).orElseThrow();
        assertThat(saved.getCodeHash()).isNotEqualTo(code.getValue());
        assertThat(encoder.matches(code.getValue(), saved.getCodeHash())).isTrue();
    }

    @Test
    void enforcesThirtySecondsIncludingExactBoundary() throws Exception {
        service.send(EMAIL);
        when(clock.instant()).thenReturn(NOW.plusSeconds(30).minusNanos(1));
        mvc.perform(post("/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"verify@example.com\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(CommonResponseAssertions::assertCommonResponse)
                .andExpect(jsonPath("$.code").value("EMAIL_RESEND_TOO_SOON"));
        verify(mail, times(1)).sendCode(anyString(), anyString());
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));
        service.send(EMAIL);
        assertThat(verifications.findById(EMAIL).orElseThrow().getExpiresAt()).isEqualTo(NOW.plusSeconds(210));
        verify(mail, times(2)).sendCode(anyString(), anyString());
    }

    @Test
    void rejectsRegisteredEmailWithoutSendingMail() throws Exception {
        var beach = beaches.saveAndFlush(Beach.create("인증 테스트 해변"));
        var user = users.saveAndFlush(User.createGuard(EMAIL, "unused", beach));
        try {
            mvc.perform(post("/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"verify@example.com\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
            verifyNoInteractions(mail);
        } finally {
            users.deleteById(user.getId());
            beaches.deleteById(beach.getId());
        }
    }

    @Test
    void rejectsMalformedAndMissingEmail() throws Exception {
        for (String body : new String[]{"{}", "{\"email\":\"\"}", "{\"email\":\"invalid\"}",
                "{\"email\":\"" + "a".repeat(31) + "@a.co\"}", "{"}) {
            mvc.perform(post("/auth/email-verifications").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(CommonResponseAssertions::assertCommonResponse);
        }
        verifyNoInteractions(mail);
    }

    @Test
    void failedDeliveryDoesNotStartCooldownOrInvalidatePreviousCode() throws Exception {
        doThrow(new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED)).when(mail).sendCode(anyString(), anyString());
        mvc.perform(post("/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"verify@example.com\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_FAILED"));
        assertThat(verifications.findById(EMAIL).orElseThrow().getCodeHash()).isNull();
        doNothing().when(mail).sendCode(anyString(), anyString());
        service.send(EMAIL);
        var previous = verifications.findById(EMAIL).orElseThrow().getCodeHash();
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));
        doThrow(new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED)).when(mail).sendCode(anyString(), anyString());
        assertThatThrownBy(() -> service.send(EMAIL)).isInstanceOf(BusinessException.class);
        assertThat(verifications.findById(EMAIL).orElseThrow().getCodeHash()).isEqualTo(previous);
    }

    @Test
    void concurrentFirstRequestsSendOnlyOneCode() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<String> issue = () -> {
                start.await();
                try { service.send(EMAIL); return "SENT"; }
                catch (BusinessException error) { return error.getErrorCode().name(); }
            };
            var first = executor.submit(issue);
            var second = executor.submit(issue);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SENT", "EMAIL_RESEND_TOO_SOON");
            verify(mail, times(1)).sendCode(eq(EMAIL), anyString());
        }
    }
}
