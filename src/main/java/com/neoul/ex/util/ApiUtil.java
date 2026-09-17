package com.neoul.ex.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class ApiUtil {

    private ApiUtil() {
    }

    public static <T> ApiResult<T> success(T response) {
        return new ApiResult<>(true, response, null);
    }

    public static ApiResult<Void> error(HttpStatus status, String message) {
        return error(status, message, null);
    }

    public static ApiResult<Void> error(
            HttpStatus status,
            String message,
            Map<String, String> errors
    ) {
        return new ApiResult<>(false, null, new ApiError(status.value(), message, errors));
    }

    public record ApiResult<T>(boolean success, T response, ApiError error) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ApiError(int status, String message, Map<String, String> errors) {
    }
}
