package com.neoul.ex.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.neoul.ex.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String TEST_SECRET = "test-secret-must-be-at-least-thirty-two-bytes";

    @Test
    void containsUserIdSubjectRoleClaimAndExpiration() {
        JwtProvider jwtProvider = new JwtProvider(TEST_SECRET, 3_600_000L);
        String token = jwtProvider.createAccessToken(1L, Role.GUARD);

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("role", String.class)).isEqualTo("GUARD");
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(jwtProvider.getAccessTokenExpirationSeconds()).isEqualTo(3600L);
    }
}
