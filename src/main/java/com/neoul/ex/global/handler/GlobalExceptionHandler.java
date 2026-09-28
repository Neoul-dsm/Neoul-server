package com.neoul.ex.global.handler;

import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;
import com.neoul.ex.global.handler.response.Message;
import com.neoul.ex.global.handler.response.ValidationErrorResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        var details = new ValidationErrorResponse(errors.entrySet().stream()
                .map(error -> new ValidationErrorResponse.Violation(error.getKey(), error.getValue())).toList());
        return handleExceptionInternal(exception, Message.failure(ErrorCode.INVALID_INPUT, details), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        Object responseBody = body instanceof Message<?> ? body : frameworkFailure(status);
        return super.handleExceptionInternal(exception, responseBody, responseHeaders, status, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Message<Void>> handleBusinessException(BusinessException exception) {
        var response = ResponseEntity.status(exception.getStatus()).contentType(MediaType.APPLICATION_JSON);
        if (exception.getStatus() == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(Message.failure(exception.getErrorCode(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Message<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled API exception", exception);
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
                .body(Message.failure(ErrorCode.INTERNAL_SERVER_ERROR, null));
    }

    private Message<Void> frameworkFailure(HttpStatusCode status) {
        if (status.value() == 400) {
            return Message.failure(ErrorCode.INVALID_INPUT, null);
        }
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String code = httpStatus == null ? "HTTP_ERROR" : httpStatus.name();
        String message = switch (status.value()) {
            case 404 -> "요청한 경로를 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 HTTP 메서드입니다.";
            case 406 -> "지원하지 않는 응답 형식입니다.";
            case 413 -> "요청 본문이 너무 큽니다.";
            case 415 -> "지원하지 않는 요청 형식입니다.";
            case 503 -> "일시적으로 요청을 처리할 수 없습니다.";
            default -> "요청을 처리하지 못했습니다.";
        };
        return Message.failure(status, code, message, null);
    }
}
