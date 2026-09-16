package com.neoul.ex.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.neoul.ex.global.security.CustomUserDetails;
import com.neoul.ex.global.security.CustomUserDetailsService;
import com.neoul.ex.user.entity.Role;
import com.neoul.ex.user.entity.User;
import com.neoul.ex.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void loadsUserByEmail() throws Exception {
        User user = User.createGuard("guard@example.com", "encoded-password", null);
        setId(user, 1L);
        when(userRepository.findByEmail("guard@example.com")).thenReturn(Optional.of(user));

        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        CustomUserDetails details = (CustomUserDetails) service.loadUserByUsername("guard@example.com");

        assertThat(details.getUserId()).isEqualTo(1L);
        assertThat(details.getUsername()).isEqualTo("guard@example.com");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_" + Role.GUARD.name());
    }

    @Test
    void throwsWhenEmailDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void daoAuthenticationProviderUsesBcryptForPasswordVerification() throws Exception {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        User user = User.createGuard("guard@example.com", passwordEncoder.encode("Abcd1234!"), null);
        setId(user, 1L);
        when(userRepository.findByEmail("guard@example.com")).thenReturn(Optional.of(user));

        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(
                new CustomUserDetailsService(userRepository)
        );
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        var authentication = authenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("guard@example.com", "Abcd1234!")
        );

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(CustomUserDetails.class);
        assertThatThrownBy(() -> authenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("guard@example.com", "Wrong1234!")
        )).isInstanceOf(BadCredentialsException.class);
    }

    private void setId(User user, Long id) throws Exception {
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}
