package com.neoul.ex.global.handler.response;

import java.util.List;

public record ValidationErrorResponse(List<Violation> fieldErrors) {
    public ValidationErrorResponse {
        fieldErrors = List.copyOf(fieldErrors);
    }

    public record Violation(String field, String message) {
    }
}