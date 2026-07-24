package com.hsbc.payment.exception;

import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.dto.response.ErrorResponse;
import com.hsbc.payment.enums.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus httpStatus = mapToHttpStatus(ex.getErrorCode());
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode().name())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .build();
        return ResponseEntity.status(httpStatus).body(ApiResponse.fail(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.name())
                .message("Field validation failed")
                .details(details)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.PROCESSING_ERROR.name())
                .message(ex.getMessage() != null ? ex.getMessage() : "Internal server error")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(error));
    }

    private HttpStatus mapToHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_FAILED, INSUFFICIENT_FUNDS, INVALID_ACCOUNT,
                 INVALID_CURRENCY, INVALID_AMOUNT, INVALID_STATUS_TRANSITION -> HttpStatus.BAD_REQUEST;
            case DUPLICATE_PAYMENT -> HttpStatus.CONFLICT;
            case PAYMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RISK_BLOCKED -> HttpStatus.FORBIDDEN;
            case NETWORK_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROCESSING_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
