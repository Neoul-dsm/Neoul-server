package com.neoul.ex.global.handler.response;

import com.neoul.ex.global.exception.ErrorCode;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatusCode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record Message<T>(boolean success, int status, String code, String message, T data) {

    public static <T> Message<T> success(SuccessCode code, T data) {
        return new Message<>(true, code.getStatus().value(), code.name(), code.getMessage(), data);
    }

    public static <T> Message<T> failure(ErrorCode code, T data) {
        return failure(code.getStatus(), code.name(), code.getMessage(), data);
    }

    public static <T> Message<T> failure(HttpStatusCode status, String code, String message, T data) {
        if (!status.isError()) {
            throw new IllegalArgumentException("Failure responses require an error HTTP status");
        }
        return new Message<>(false, status.value(), code, message, data);
    }
}
