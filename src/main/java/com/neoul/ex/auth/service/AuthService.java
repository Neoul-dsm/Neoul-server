package com.neoul.ex.auth.service;

import com.neoul.ex.auth.dto.LoginRequest;
import com.neoul.ex.auth.dto.LoginResponse;
import com.neoul.ex.auth.dto.SignupRequest;
import com.neoul.ex.auth.dto.SignupResponse;
import com.neoul.ex.global.security.CustomUserDetails;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.beach.entity.Beach;
import com.neoul.ex.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.user.entity.User;
import com.neoul.ex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    public LoginResponse login(LoginRequest request) {
        if (!userRepository.existsByEmail(request.email())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "존재하지 않는 이메일입니다.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password())
            );
            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
            String accessToken = jwtProvider.createAccessToken(principal.getUserId(), principal.getRole());

            return new LoginResponse(accessToken, "Bearer", jwtProvider.getAccessTokenExpirationSeconds());
        } catch (AuthenticationException exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다.");
        }
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
        }

        Beach beach = beachRepository.findById(request.beachId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 해수욕장입니다."));

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.createGuard(request.email(), encodedPassword, beach);
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
        }

        return SignupResponse.from(savedUser);
    }
}
