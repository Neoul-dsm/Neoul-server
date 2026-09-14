package com.neoul.ex.auth.dto;

import com.neoul.ex.user.entity.User;

public record SignupResponse(
        Long id,
        String loginId,
        Long beachId
) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getLoginId(), user.getBeach().getId());
    }
}
