package com.neoul.ex.domain.auth.dto;

import com.neoul.ex.domain.user.entity.User;


public record SignupResponse(
        Long id,
        String email,
        Long beachId,
        String name
) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getBeach().getId(), user.getName());
    }
}
