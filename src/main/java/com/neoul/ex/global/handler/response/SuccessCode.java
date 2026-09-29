package com.neoul.ex.global.handler.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
    EMAIL_VERIFIED(HttpStatus.OK, "이메일 인증이 완료되었습니다."),
    EMAIL_VERIFICATION_SENT(HttpStatus.OK, "인증 코드를 발송했습니다."),
    SIGNUP_COMPLETED(HttpStatus.CREATED, "회원가입이 완료되었습니다."),
    LOGIN_SUCCEEDED(HttpStatus.OK, "로그인에 성공했습니다."),
    LOGOUT_COMPLETED(HttpStatus.OK, "로그아웃이 완료되었습니다."),
    DROWNING_ALERT_STREAM_CONNECTED(HttpStatus.OK, "익수자 감지 알림에 연결되었습니다."),
    DROWNING_DETECTED(HttpStatus.OK, "익수자가 감지되었습니다."),
    MARINE_LIFE_ALERT_STREAM_CONNECTED(HttpStatus.OK, "유해 생물 탐지 알림에 연결되었습니다."),
    MARINE_LIFE_DETECTED(HttpStatus.OK, "유해 생물이 감지되었습니다."),
    BEACH_LIST_RETRIEVED(HttpStatus.OK, "해수욕장 목록을 조회했습니다."),
    BEACH_RETRIEVED(HttpStatus.OK, "해수욕장 정보를 조회했습니다."),
    SHIP_LIST_RETRIEVED(HttpStatus.OK, "배 목록을 조회했습니다."),
    SHIP_RETRIEVED(HttpStatus.OK, "배 정보를 조회했습니다."),
    SHIP_API_KEY_ISSUED(HttpStatus.OK, "무인배 API 키를 발급했습니다."),
    SHIP_API_KEY_REVOKED(HttpStatus.OK, "무인배 API 키를 폐기했습니다."),
    SHIP_STATUS_RECEIVED(HttpStatus.OK, "무인배 상태를 수신했습니다."),
    SHIP_CONNECTION_RETRIEVED(HttpStatus.OK, "통신 상태를 조회했습니다."),
    SHIP_LOCATION_RETRIEVED(HttpStatus.OK, "배 위치를 조회했습니다.");

    private final HttpStatus status;
    private final String message;
}
