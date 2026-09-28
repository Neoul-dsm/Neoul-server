package com.neoul.ex.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record EmailVerificationRequest(
        @NotBlank @Email @Size(max = 30) String email) {
    public EmailVerificationRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
