package com.neoul.ex.global.security;

import com.neoul.ex.domain.user.entity.enums.Role;

import java.security.Principal;
import java.time.Instant;

public record AccessTokenPrincipal(Long userId, Role role, String tokenHash, Instant expiresAt) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
