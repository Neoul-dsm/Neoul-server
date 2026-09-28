package com.neoul.ex.domain.ship.controller;

import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.global.config.OpenApiConfig;
import com.neoul.ex.global.handler.response.Message;
import com.neoul.ex.global.handler.response.SuccessCode;
import com.neoul.ex.domain.ship.dto.ShipBatteryResponse;
import com.neoul.ex.domain.ship.dto.ShipConnectionResponse;
import com.neoul.ex.domain.ship.dto.ShipLocationResponse;
import com.neoul.ex.domain.ship.dto.ShipResponse;
import com.neoul.ex.domain.ship.dto.ShipSolarPowerResponse;
import com.neoul.ex.domain.ship.service.ShipService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "배")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RequestMapping("/ships")
@RequiredArgsConstructor
public class ShipController {
    private final ShipService shipService;

    @GetMapping
    @Operation(summary = "배 목록 조회")
    public ResponseEntity<Message<List<ShipResponse>>> getShips(@RequestParam(required = false) Long beachId,
            @AuthenticationPrincipal AccessTokenPrincipal principal)
    {
        return ResponseEntity.status(SuccessCode.SHIP_LIST_RETRIEVED.getStatus())
                .body(Message.success(SuccessCode.SHIP_LIST_RETRIEVED, shipService.getShips(beachId, principal)));
    }

    @GetMapping("/{shipId}")
    @Operation(summary = "배 상세 조회")
    public ResponseEntity<Message<ShipResponse>> getShip(@PathVariable Long shipId,
            @AuthenticationPrincipal AccessTokenPrincipal principal) {
        return ResponseEntity.status(SuccessCode.SHIP_RETRIEVED.getStatus())
                .body(Message.success(SuccessCode.SHIP_RETRIEVED, shipService.getShip(shipId, principal)));
    }

    @GetMapping("/{shipId}/connection")
    @Operation(summary = "통신 상태 확인", description = "마지막 수신 시각과 연결 상태를 반환하며 기본 기준으로 2분 이상 미수신이면 OFFLINE입니다")
    public ResponseEntity<Message<ShipConnectionResponse>> getConnection(@PathVariable Long shipId,
            @AuthenticationPrincipal AccessTokenPrincipal principal) {
        return ResponseEntity.ok().body(Message.success(SuccessCode.SHIP_CONNECTION_RETRIEVED,
                shipService.getConnection(shipId, principal)));
    }

    @GetMapping("/{shipId}/solar-power")
    @Operation(summary = "태양광 발전량 조회")
    public ResponseEntity<Message<ShipSolarPowerResponse>> getSolarPower(@PathVariable Long shipId,
            @AuthenticationPrincipal AccessTokenPrincipal principal) {
        return ResponseEntity.ok().body(Message.success(SuccessCode.SHIP_SOLAR_POWER_RETRIEVED,
                shipService.getSolarPower(shipId, principal)));
    }

    @GetMapping("/{shipId}/battery")
    @Operation(summary = "배터리 잔여량 조회", description = "배터리 잔여 퍼센트만 반환하며 미수신 값은 null입니다")
    public ResponseEntity<Message<ShipBatteryResponse>> getBattery(@PathVariable Long shipId,
            @AuthenticationPrincipal AccessTokenPrincipal principal) {
        return ResponseEntity.ok().body(Message.success(SuccessCode.SHIP_BATTERY_RETRIEVED,
                shipService.getBattery(shipId, principal)));
    }

    @GetMapping("/{shipId}/location")
    @Operation(summary = "배 위치 조회", description = "마지막 GPS의 위도와 경도만 반환하며 미수신 좌표는 null입니다")
    public ResponseEntity<Message<ShipLocationResponse>> getLocation(@PathVariable Long shipId,
            @AuthenticationPrincipal AccessTokenPrincipal principal) {
        return ResponseEntity.status(SuccessCode.SHIP_LOCATION_RETRIEVED.getStatus())
                .body(Message.success(SuccessCode.SHIP_LOCATION_RETRIEVED, shipService.getLocation(shipId, principal)));
    }
}
