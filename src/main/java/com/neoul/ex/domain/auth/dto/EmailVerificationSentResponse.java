package com.neoul.ex.domain.auth.dto;

import java.time.Instant;

public record EmailVerificationSentResponse(Instant expiresAt, Instant resendAvailableAt) {}
