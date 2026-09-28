package com.neoul.ex.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record EmailVerificationConfirmRequest(
        @NotBlank @Email @Size(max = 30) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}", message = "인증 코드는 숫자 6자리여야 합니다.") String code) {
    public EmailVerificationConfirmRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
