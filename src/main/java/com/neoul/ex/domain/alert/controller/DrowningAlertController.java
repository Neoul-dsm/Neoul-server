package com.neoul.ex.domain.alert.controller;

import com.neoul.ex.domain.alert.dto.DrowningAlertResponse;
import com.neoul.ex.domain.alert.service.PollingAlertStream;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.global.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "실시간 알림")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RequiredArgsConstructor
public class DrowningAlertController {
    private final PollingAlertStream<DrowningAlertResponse> streams;

    @GetMapping(value = "/alerts/drowning/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "익수자 감지 알림 구독")
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal AccessTokenPrincipal principal,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId)
    {
        return ResponseEntity.ok().contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Accel-Buffering", "no").body(streams.subscribe(principal, lastEventId));
    }
}
