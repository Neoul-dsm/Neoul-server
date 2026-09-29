package com.neoul.ex.domain.auth.controller;

import com.neoul.ex.domain.auth.dto.LoginResponse;
import com.neoul.ex.domain.auth.dto.SignupResponse;
import com.neoul.ex.domain.auth.service.AuthService;
import com.neoul.ex.domain.auth.service.TokenSessionService;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.global.handler.GlobalExceptionHandler;
import com.neoul.ex.global.testutil.CommonResponseAssertions;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {
    private static final String LOGIN_BODY = """
            {"email":"guard@example.com","password":"Abcd1234!"}
            """;
    private static final String SIGNUP_BODY = """
            {"email":"guard@example.com","password":"Abcd1234!","passwordConfirm":"Abcd1234!","beachId":2,"name":"홍길동","verificationToken":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
            """;
    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, mock(TokenSessionService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .alwaysExpect(CommonResponseAssertions::assertCommonResponse)
                .build();
    }

    @Test
    void returnsAccessTokenForSuccessfulLogin() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("access-token", "Bearer", 3600L));
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCEEDED"))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void returnsUnauthorizedResponseForUnknownEmailAndWrongPassword() throws Exception {
        for (ErrorCode code : new ErrorCode[]{ErrorCode.EMAIL_NOT_FOUND, ErrorCode.INVALID_PASSWORD}) {
            doThrow(new BusinessException(code)).when(authService).login(any());
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(code.name()))
                    .andExpect(jsonPath("$.message").value(code.getMessage()))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY.replace("guard@example.com", "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.data.fieldErrors[0].message").value("이메일 형식이 올바르지 않습니다."));
    }

    @Test
    void returnsCreatedSignupWithoutExposingPassword() throws Exception {
        when(authService.signup(any())).thenReturn(new SignupResponse(1L, "guard@example.com", 2L, "홍길동"));
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SIGNUP_COMPLETED"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("guard@example.com"))
                .andExpect(jsonPath("$.data.beachId").value(2))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void preservesSignupBusinessErrorCodesAndStatuses() throws Exception {
        for (ErrorCode code : new ErrorCode[]{ErrorCode.DUPLICATE_EMAIL,
                ErrorCode.PASSWORD_CONFIRM_MISMATCH, ErrorCode.BEACH_NOT_FOUND}) {
            doThrow(new BusinessException(code)).when(authService).signup(any());
            mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                    .andExpect(status().is(code.getStatus().value()))
                    .andExpect(jsonPath("$.code").value(code.name()))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }
    }

    @Test
    void formatsMalformedAndMissingJsonBodies() throws Exception {
        for (String body : new String[]{"{", ""}) {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void formatsUnsupportedMediaAndMethodsWhilePreservingAllowHeader() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("invalid"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void formatsUnacceptableResponseType() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("access-token", "Bearer", 3600L));
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML).content(LOGIN_BODY))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        when(authService.login(any())).thenThrow(new IllegalStateException("private database information"));
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
