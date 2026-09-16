package com.neoul.ex.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neoul.ex.auth.dto.SignupRequest;
import com.neoul.ex.auth.dto.SignupResponse;
import com.neoul.ex.global.security.JwtProvider;
import com.neoul.ex.beach.entity.Beach;
import com.neoul.ex.beach.repository.BeachRepository;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.user.entity.User;
import com.neoul.ex.user.repository.UserRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BeachRepository beachRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtProvider jwtProvider;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                beachRepository,
                passwordEncoder,
                authenticationManager,
                jwtProvider
        );
    }

    @Test
    void signUpGuardWithEncodedPasswordAndSelectedBeach() throws Exception {
        Beach beach = beach(1L, "해운대해수욕장");
        when(userRepository.existsByEmail("guard@example.com")).thenReturn(false);
        when(beachRepository.findById(1L)).thenReturn(Optional.of(beach));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> user(10L, invocation.getArgument(0)));

        SignupResponse response = authService.signup(
                new SignupRequest(" Guard@Example.com ", "Abcd1234!", "Abcd1234!", 1L)
        );

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("guard@example.com");
        assertThat(response.beachId()).isEqualTo(1L);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("guard@example.com");
        assertThat(userCaptor.getValue().getRole()).hasToString("GUARD");
        assertThat(passwordEncoder.matches("Abcd1234!", userCaptor.getValue().getPassword())).isTrue();
    }

    @Test
    void failsWhenPasswordsDoNotMatch() {
        SignupRequest request = new SignupRequest("guard@example.com", "Abcd1234!", "Different1!", 1L);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
    }

    @Test
    void failsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("guard@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("GUARD@example.com", "Abcd1234!", "Abcd1234!", 1L)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");
    }

    @Test
    void failsWhenBeachDoesNotExist() {
        when(userRepository.existsByEmail("guard@example.com")).thenReturn(false);
        when(beachRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("guard@example.com", "Abcd1234!", "Abcd1234!", 999L)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 해수욕장입니다.");
    }

    @Test
    void returnsConflictWhenUniqueConstraintRejectsConcurrentSignup() throws Exception {
        Beach beach = beach(1L, "해운대해수욕장");
        when(userRepository.existsByEmail("guard@example.com")).thenReturn(false);
        when(beachRepository.findById(1L)).thenReturn(Optional.of(beach));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("guard@example.com", "Abcd1234!", "Abcd1234!", 1L)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");
    }

    private Beach beach(Long id, String name) throws Exception {
        Constructor<Beach> constructor = Beach.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Beach beach = constructor.newInstance();
        setField(beach, "id", id);
        setField(beach, "name", name);
        return beach;
    }

    private User user(Long id, User user) throws Exception {
        setField(user, "id", id);
        return user;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
