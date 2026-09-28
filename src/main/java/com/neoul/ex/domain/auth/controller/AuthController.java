package com.neoul.ex.domain.auth.controller;

import com.neoul.ex.domain.auth.dto.LoginRequest;
import com.neoul.ex.domain.auth.dto.LoginResponse;
import com.neoul.ex.domain.auth.dto.SignupRequest;
import com.neoul.ex.domain.auth.dto.SignupResponse;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.domain.auth.service.AuthService;
import com.neoul.ex.domain.auth.service.TokenSessionService;
import com.neoul.ex.global.config.OpenApiConfig;
import com.neoul.ex.global.handler.response.Message;
import com.neoul.ex.global.handler.response.SuccessCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "인증")
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final TokenSessionService tokenSessionService;

    @PostMapping("/register")
    @Operation(summary = "회원가입")
    @ApiResponse(responseCode = "201", description = "회원가입 완료", useReturnTypeSchema = true)
    public ResponseEntity<Message<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(SuccessCode.SIGNUP_COMPLETED.getStatus())
                .body(Message.success(SuccessCode.SIGNUP_COMPLETED, authService.signup(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인")
    public ResponseEntity<Message<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.status(SuccessCode.LOGIN_SUCCEEDED.getStatus())
                .body(Message.success(SuccessCode.LOGIN_SUCCEEDED, authService.login(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public ResponseEntity<Message<Void>> logout(@AuthenticationPrincipal AccessTokenPrincipal principal) {
        tokenSessionService.logout(principal);
        return ResponseEntity.status(SuccessCode.LOGOUT_COMPLETED.getStatus())
                .body(Message.success(SuccessCode.LOGOUT_COMPLETED, null));
    }
}
