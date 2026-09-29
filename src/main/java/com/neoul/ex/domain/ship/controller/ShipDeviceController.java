package com.neoul.ex.domain.ship.controller;

import com.neoul.ex.domain.ship.dto.ShipApiKeyResponse;
import com.neoul.ex.domain.ship.dto.ShipStatusRequest;
import com.neoul.ex.domain.ship.service.ShipDeviceService;
import com.neoul.ex.global.config.OpenApiConfig;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.global.handler.response.Message;
import com.neoul.ex.global.handler.response.SuccessCode;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ships/{shipId}")
@Tag(name = "무인배 인증과 수신")
@RequiredArgsConstructor
public class ShipDeviceController {
    private final ShipDeviceService devices;

    @PostMapping("/api-key")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "무인배 API 키 발급 또는 교체", description = "ADMIN만 가능하며 원문 키는 이 응답에서만 제공됩니다")
    public ResponseEntity<Message<ShipApiKeyResponse>> issueApiKey(@PathVariable Long shipId,
            @AuthenticationPrincipal AccessTokenPrincipal principal) {
        String apiKey = devices.issueApiKey(shipId, principal);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Message.success(SuccessCode.SHIP_API_KEY_ISSUED, new ShipApiKeyResponse(shipId, apiKey)));
    }

    @DeleteMapping("/api-key")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "무인배 API 키 폐기", description = "ADMIN만 가능합니다")
    public ResponseEntity<Message<Void>> revokeApiKey(@PathVariable Long shipId,
            @AuthenticationPrincipal AccessTokenPrincipal principal) {
        devices.revokeApiKey(shipId, principal);
        return ResponseEntity.ok().body(Message.success(SuccessCode.SHIP_API_KEY_REVOKED, null));
    }

    @PostMapping("/status")
    @SecurityRequirement(name = OpenApiConfig.SHIP_API_KEY)
    @Operation(summary = "무인배 위치와 통신 상태 수신",
            description = "배에 발급한 X-API-Key가 필요합니다 좌표는 함께 보내거나 모두 생략합니다 수신 시각은 서버에서 기록합니다")
    public ResponseEntity<Message<Void>> receiveStatus(@PathVariable Long shipId,
            @Parameter(hidden = true) @RequestHeader(value = "X-API-Key", required = false) List<String> apiKeys,
            @RequestBody ShipStatusRequest request) {
        if (apiKeys == null || apiKeys.size() != 1) throw new BusinessException(ErrorCode.INVALID_SHIP_API_KEY);
        devices.receiveStatus(shipId, apiKeys.getFirst(), request.latitude(), request.longitude());
        return ResponseEntity.ok().body(Message.success(SuccessCode.SHIP_STATUS_RECEIVED, null));
    }
}
