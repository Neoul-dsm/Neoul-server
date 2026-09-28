package com.neoul.ex.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    EMAIL_CODE_INVALID(StatusEnum.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    EMAIL_CODE_EXPIRED(StatusEnum.BAD_REQUEST, "인증 코드가 만료되었습니다."),
    EMAIL_CODE_ATTEMPTS_EXCEEDED(StatusEnum.TOO_MANY_REQUESTS, "인증 코드 확인 횟수를 초과했습니다. 다시 발급해 주세요."),
    EMAIL_NOT_VERIFIED(StatusEnum.FORBIDDEN, "이메일 인증을 완료해 주세요."),
    EMAIL_RESEND_TOO_SOON(StatusEnum.TOO_MANY_REQUESTS, "인증 코드는 30초 후 재발급할 수 있습니다."),
    EMAIL_DELIVERY_FAILED(StatusEnum.SERVICE_UNAVAILABLE, "인증 이메일을 발송하지 못했습니다."),

    INVALID_INPUT(StatusEnum.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    PASSWORD_CONFIRM_MISMATCH(StatusEnum.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_FOUND(StatusEnum.UNAUTHORIZED, "존재하지 않는 이메일입니다."),
    INVALID_PASSWORD(StatusEnum.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(StatusEnum.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_TOKEN(StatusEnum.UNAUTHORIZED, "유효하지 않은 인증 토큰입니다."),
    TOKEN_EXPIRED(StatusEnum.UNAUTHORIZED, "인증 토큰이 만료되었습니다."),
    TOKEN_REVOKED(StatusEnum.UNAUTHORIZED, "로그아웃된 인증 토큰입니다."),
    BEACH_NOT_FOUND(StatusEnum.NOT_FOUND, "존재하지 않는 해수욕장입니다."),
    SHIP_NOT_FOUND(StatusEnum.NOT_FOUND, "존재하지 않는 배입니다."),
    DUPLICATE_EMAIL(StatusEnum.CONFLICT, "이미 사용 중인 이메일입니다."),
    FORBIDDEN(StatusEnum.FORBIDDEN, "접근이 거부되었습니다."),
    BEACH_NOT_ASSIGNED(StatusEnum.FORBIDDEN, "소속 해수욕장이 지정되지 않았습니다."),
    TOO_MANY_ALERT_CONNECTIONS(StatusEnum.TOO_MANY_REQUESTS, "알림 연결 수가 허용된 한도를 초과했습니다."),
    INTERNAL_SERVER_ERROR(StatusEnum.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final StatusEnum status;
    private final String message;
}
