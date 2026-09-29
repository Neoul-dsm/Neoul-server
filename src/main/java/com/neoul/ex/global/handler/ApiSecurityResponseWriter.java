package com.neoul.ex.global.handler;

import com.neoul.ex.global.handler.response.Message;
import com.neoul.ex.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ApiSecurityResponseWriter {
    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (code.getStatus().value() == 401) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        jsonMapper.writeValue(response.getOutputStream(), Message.failure(code, null));
    }
}
