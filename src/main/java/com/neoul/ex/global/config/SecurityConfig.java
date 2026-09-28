package com.neoul.ex.global.config;

import com.neoul.ex.global.handler.ApiAccessDeniedHandler;
import com.neoul.ex.global.handler.ApiSecurityResponseWriter;
import com.neoul.ex.global.security.CustomUserDetailsService;
import com.neoul.ex.global.security.JwtAuthenticationFilter;
import com.neoul.ex.domain.auth.service.TokenSessionService;

import jakarta.servlet.DispatcherType;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider,
            ApiAccessDeniedHandler accessDeniedHandler,
            TokenSessionService tokenSessionService,
            ApiSecurityResponseWriter responseWriter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(new JwtAuthenticationFilter(tokenSessionService, responseWriter),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(accessDeniedHandler)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(request -> request.getDispatcherType() == DispatcherType.ASYNC
                            && (request.getRequestURI().equals(request.getContextPath() + "/alerts/drowning/stream")
                            || request.getRequestURI().equals(request.getContextPath() + "/alerts/marineanimal/stream")))
                            .permitAll();
                    authorize.requestMatchers(HttpMethod.GET,
                            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/email-verifications").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/email-verifications/confirm").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").hasAnyRole("ADMIN", "GUARD")
                        .requestMatchers(HttpMethod.GET, "/alerts/drowning/stream").hasRole("GUARD")
                        .requestMatchers(HttpMethod.GET, "/alerts/marineanimal/stream").hasAnyRole("ADMIN", "GUARD")
                        .requestMatchers(HttpMethod.GET, "/ships/*/solar-power").hasAnyRole("ADMIN", "GUARD")
                        .requestMatchers(HttpMethod.GET, "/ships", "/ships/*", "/ships/*/location",
                                "/ships/*/connection", "/ships/*/battery")
                        .hasAnyRole("ADMIN", "GUARD")
                        .requestMatchers(HttpMethod.GET, "/beaches/**").permitAll()
                        .anyRequest().authenticated();
                })
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return authenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
