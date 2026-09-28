package com.neoul.ex.global.handler.response;

import com.neoul.ex.global.exception.ErrorCode;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class Message<T> {
    private final boolean success;
    private final int status;
    private final String code;
    private final String message;
    private final T data;

    private Message(boolean success, int status, String code, String message, T data) {
        this.success = success;
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Message<T> success(SuccessCode code, T data) {
        return new Message<>(true,
                                code.getStatus().value(),
                                code.name(),
                                code.getMessage(),
                                data);
    }

    public static <T> Message<T> failure(ErrorCode code, T data) {
        return failure(code.getStatus().toHttpStatus(), code.name(), code.getMessage(), data);
    }

    public static <T> Message<T> failure(HttpStatusCode status, String code, String message, T data) {
        if (!status.isError()) {
            throw new IllegalArgumentException("Failure responses require an error HTTP status");
        }
        return new Message<>(false, status.value(), code, message, data);
    }
}