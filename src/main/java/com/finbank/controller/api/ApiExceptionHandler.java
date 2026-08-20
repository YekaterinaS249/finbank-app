package com.finbank.controller.api;

import com.finbank.dto.ApiError;
import com.finbank.dto.FieldError;
import com.finbank.exception.BusinessRuleException;
import com.finbank.exception.RegistrationValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(basePackages = "com.finbank.controller.api")
public class ApiExceptionHandler {

    @ExceptionHandler(RegistrationValidationException.class)
    public ResponseEntity<ApiError> handleRegistrationValidation(RegistrationValidationException ex,
                                                                   HttpServletRequest request) {
        String errorLabel = ex.getStatus() == HttpStatus.CONFLICT ? "CONFLICT" : "VALIDATION_ERROR";
        ApiError body = new ApiError(ex.getStatus().value(), errorLabel, ex.getMessage(),
                ex.getErrors(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        ApiError body = new ApiError(ex.getStatus().value(), ex.getStatus().getReasonPhrase(),
                ex.getMessage(), List.of(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), "VALIDATION_ERROR", fe.getDefaultMessage()))
                .toList();
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "Request failed validation", details, request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        ApiError body = new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "Something went wrong. Please try again.", List.of(), request.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }
}
