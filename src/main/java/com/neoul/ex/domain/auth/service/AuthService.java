package com.neoul.ex.domain.auth.service;

import com.neoul.ex.domain.auth.dto.LoginRequest;
import com.neoul.ex.domain.auth.dto.LoginResponse;
import com.neoul.ex.domain.auth.dto.SignupRequest;
import com.neoul.ex.domain.auth.dto.SignupResponse;
import com.neoul.ex.domain.user.entity.User;
import com.neoul.ex.domain.user.repository.UserRepository;
import com.neoul.ex.global.security.CustomUserDetails;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.domain.beach.entity.Beach;
import com.neoul.ex.domain.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BeachRepository beachRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final EmailVerificationService emailVerificationService;

    public LoginResponse login(LoginRequest request) {
        if (!userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_FOUND);
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password())
            );
            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
            String accessToken = jwtProvider.createAccessToken(principal.getUserId(), principal.getRole());

            return new LoginResponse(accessToken, "Bearer", jwtProvider.getAccessTokenExpirationSeconds());
        } catch (AuthenticationException exception) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        emailVerificationService.consume(request.email(), request.verificationToken());

        Beach beach = beachRepository.findById(request.beachId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BEACH_NOT_FOUND));

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.createGuard(request.email(), encodedPassword, beach, request.name());
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        return SignupResponse.from(savedUser, beach.getId());
    }
}
