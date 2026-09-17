package com.neoul.ex.dto;

import com.neoul.ex.entity.User;

public record SignupResponse(
        Long id,
        String email,
        Long beachId
) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getBeach().getId());
    }
}
