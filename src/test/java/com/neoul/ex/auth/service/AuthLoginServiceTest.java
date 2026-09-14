package com.neoul.ex.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.neoul.ex.auth.dto.LoginRequest;
import com.neoul.ex.auth.dto.LoginResponse;
import com.neoul.ex.auth.security.CustomUserDetails;
import com.neoul.ex.auth.security.JwtProvider;
import com.neoul.ex.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.user.entity.Role;
import com.neoul.ex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthLoginServiceTest {

    private static final String INVALID_LOGIN_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    @Mock
    private UserRepository userRepository;

    @Mock
    private BeachRepository beachRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtProvider jwtProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                beachRepository,
                new BCryptPasswordEncoder(),
                authenticationManager,
                jwtProvider
        );
    }

    @Test
    void returnsAccessTokenForAuthenticatedUser() {
        CustomUserDetails principal = new CustomUserDetails(1L, "guard123", "encoded-password", Role.GUARD);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtProvider.createAccessToken(1L, Role.GUARD)).thenReturn("access-token");
        when(jwtProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("guard123", "Abcd1234!"));

        assertThat(response).isEqualTo(new LoginResponse("access-token", "Bearer", 3600L));
    }

    @Test
    void returnsSameUnauthorizedErrorForUnknownLoginIdAndWrongPassword() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new UsernameNotFoundException("not found"))
                .thenThrow(new BadCredentialsException("bad password"));

        BusinessException unknownLoginIdException = exceptionFor(new LoginRequest("unknown", "Abcd1234!"));
        BusinessException wrongPasswordException = exceptionFor(new LoginRequest("guard123", "Wrong1234!"));

        assertThat(unknownLoginIdException.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPasswordException.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownLoginIdException.getMessage()).isEqualTo(INVALID_LOGIN_MESSAGE);
        assertThat(wrongPasswordException.getMessage()).isEqualTo(INVALID_LOGIN_MESSAGE);
    }

    private BusinessException exceptionFor(LoginRequest request) {
        return (BusinessException) org.assertj.core.api.ThrowableAssert.catchThrowable(
                () -> authService.login(request)
        );
    }
}
