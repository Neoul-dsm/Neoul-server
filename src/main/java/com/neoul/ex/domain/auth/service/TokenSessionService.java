package com.neoul.ex.domain.auth.service;

import com.neoul.ex.domain.auth.entity.RevokedAccessToken;
import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.auth.repository.RevokedAccessTokenRepository;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenSessionService {
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final RevokedAccessTokenRepository revokedTokens;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AccessTokenPrincipal authenticate(String token) {
        AccessTokenPrincipal principal = jwtProvider.parseAccessToken(token);
        var user = requireActiveUser(principal);
        return new AccessTokenPrincipal(user.getId(), user.getRole(), principal.tokenHash(), principal.expiresAt());
    }

    // Also used while an SSE connection is open so logout and account changes take effect.
    @Transactional(readOnly = true)
    public User requireActiveUser(AccessTokenPrincipal principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (!principal.expiresAt().isAfter(clock.instant())) throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        rejectRevokedToken(principal.tokenHash());
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    }

    @Transactional
    public void logout(AccessTokenPrincipal principal) {
        // Serialize concurrent logout requests for this user before checking and inserting the token.
        userRepository.findByIdForUpdate(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        rejectRevokedToken(principal.tokenHash());
        revokedTokens.save(new RevokedAccessToken(principal.tokenHash(), principal.expiresAt()));
    }

    private void rejectRevokedToken(String tokenHash) {
        if (revokedTokens.existsById(tokenHash)) {
            throw new BusinessException(ErrorCode.TOKEN_REVOKED);
        }
    }
}
