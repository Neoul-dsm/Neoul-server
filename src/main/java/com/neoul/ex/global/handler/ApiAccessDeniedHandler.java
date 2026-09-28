package com.neoul.ex.global.handler;

import com.neoul.ex.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ApiSecurityResponseWriter responseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        ErrorCode code = path.equals("/auth/logout") || path.equals("/alerts/drowning/stream")
                || path.equals("/alerts/marineanimal/stream")
                ? ErrorCode.UNAUTHORIZED : ErrorCode.FORBIDDEN;
        responseWriter.write(response, code);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        responseWriter.write(response, ErrorCode.FORBIDDEN);
    }
}
