package com.neoul.ex.dto;

public record LoginResponse(

        String accessToken,

        String tokenType,

        long expiresIn
) {
}
