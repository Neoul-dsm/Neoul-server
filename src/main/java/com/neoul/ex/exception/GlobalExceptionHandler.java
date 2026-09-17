package com.neoul.ex.exception;

import com.neoul.ex.util.ApiUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiUtil.ApiResult<Void>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ApiUtil.ApiResult<Void> response = ApiUtil.error(
                HttpStatus.BAD_REQUEST,
                "입력값이 올바르지 않습니다.",
                errors
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiUtil.ApiResult<Void>> handleBusinessException(BusinessException exception) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status)
                .body(ApiUtil.error(status, exception.getMessage()));
    }
}
