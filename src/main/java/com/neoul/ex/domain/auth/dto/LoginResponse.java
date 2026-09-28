package com.neoul.ex.domain.auth.dto;

public record LoginResponse(

        String accessToken,

        String tokenType,

        long expiresIn
) {
}
