package com.neoul.ex.domain.auth.dto;

import java.time.Instant;

public record EmailVerificationConfirmedResponse(
        String verificationToken,
        Instant expiresAt
) {

}
