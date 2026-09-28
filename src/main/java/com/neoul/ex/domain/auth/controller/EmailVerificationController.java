package com.neoul.ex.domain.auth.controller;

import com.neoul.ex.domain.auth.dto.EmailVerificationConfirmRequest;
import com.neoul.ex.domain.auth.dto.EmailVerificationConfirmedResponse;
import com.neoul.ex.domain.auth.dto.EmailVerificationRequest;
import com.neoul.ex.domain.auth.dto.EmailVerificationSentResponse;
import com.neoul.ex.domain.auth.service.EmailVerificationService;
import com.neoul.ex.global.handler.response.Message;
import com.neoul.ex.global.handler.response.SuccessCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "이메일 인증")
@RequiredArgsConstructor
public class EmailVerificationController {
    private final EmailVerificationService verifications;

    @PostMapping("/auth/email-verifications/confirm")
    @Operation(summary = "이메일 인증 코드 확인")
    public ResponseEntity<Message<EmailVerificationConfirmedResponse>> confirm(
            @Valid @RequestBody EmailVerificationConfirmRequest request)
    {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Message.success(SuccessCode.EMAIL_VERIFIED, verifications.confirm(request.email(), request.code())));
    }

    @PostMapping("/auth/email-verifications")
    @Operation(summary = "이메일 인증 코드 발송")
    public ResponseEntity<Message<EmailVerificationSentResponse>> send(@Valid @RequestBody EmailVerificationRequest request)
    {
        return ResponseEntity.ok().body(Message.success(SuccessCode.EMAIL_VERIFICATION_SENT,
                verifications.send(request.email())));
    }
}
