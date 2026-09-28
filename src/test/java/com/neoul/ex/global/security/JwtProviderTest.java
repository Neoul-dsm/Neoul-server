package com.neoul.ex.global.security;

import com.neoul.ex.domain.user.entity.enums.Role;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String TEST_SECRET = "test-secret-must-be-at-least-thirty-two-bytes";

    @Test
    void everyLoginHasAUniqueTokenId() {
        JwtProvider provider = new JwtProvider(TEST_SECRET, 3_600_000L);
        String first = provider.createAccessToken(1L, Role.GUARD);
        String second = provider.createAccessToken(1L, Role.GUARD);
        assertThat(first).isNotEqualTo(second);
        assertThat(provider.parseAccessToken(first).tokenHash()).isNotEqualTo(provider.parseAccessToken(second).tokenHash());
    }

    @Test
    void rejectsSignedTokensWithoutExpiryOrValidSubjectAndRole() {
        var key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        JwtProvider provider = new JwtProvider(TEST_SECRET, 3_600_000L);
        String noExpiry = Jwts.builder().subject("1").claim("role", "GUARD").signWith(key).compact();
        String noSubject = Jwts.builder().claim("role", "GUARD").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
        String noRole = Jwts.builder().subject("1").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
        for (String token : new String[]{noExpiry, noSubject, noRole}) {
            assertThatThrownBy(() -> provider.parseAccessToken(token))
                    .isInstanceOf(com.neoul.ex.global.exception.BusinessException.class)
                    .hasMessage(com.neoul.ex.global.exception.ErrorCode.INVALID_TOKEN.getMessage());
        }
    }

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
