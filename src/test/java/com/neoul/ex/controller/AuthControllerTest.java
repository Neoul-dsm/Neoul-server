package com.neoul.ex.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neoul.ex.dto.LoginResponse;
import com.neoul.ex.service.AuthService;
import com.neoul.ex.exception.BusinessException;
import com.neoul.ex.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsAccessTokenForSuccessfulLogin() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("access-token", "Bearer", 3600L));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guard@example.com\",\"password\":\"Abcd1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void returnsUnauthorizedResponseForUnknownEmailAndWrongPassword() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "존재하지 않는 이메일입니다."))
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."));

        assertUnauthorized("unknown@example.com", "Abcd1234!", "존재하지 않는 이메일입니다.");
        assertUnauthorized("guard@example.com", "Wrong1234!", "비밀번호가 올바르지 않습니다.");
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"Abcd1234!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("이메일 형식이 올바르지 않습니다."));
    }

    private void assertUnauthorized(String email, String password, String expectedMessage) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }
}
