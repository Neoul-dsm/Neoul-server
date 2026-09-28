package com.neoul.ex.domain.auth.repository;

import com.neoul.ex.domain.auth.entity.RevokedAccessToken;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, String> {
}
