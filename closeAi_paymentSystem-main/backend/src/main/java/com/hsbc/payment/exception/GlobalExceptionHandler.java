package com.hsbc.payment.exception;

import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.dto.response.ErrorResponse;
import com.hsbc.payment.enums.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.INVALID_STATUS_TRANSITION.name())
                .message("Payment was modified by another request, please retry")
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(error));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.name())
                .message("Required header is missing: " + ex.getHeaderName())
                .build();
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.name())
                .message("Request body contains invalid JSON")
                .build();
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.PROCESSING_ERROR.name())
                .message("Internal server error")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(error));
    }

    private HttpStatus mapToHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_FAILED, INSUFFICIENT_FUNDS, INVALID_ACCOUNT,
                 INVALID_CURRENCY, INVALID_AMOUNT, INVALID_CREDENTIALS,
                 BENEFICIARY_MISMATCH, EXCHANGE_RATE_NOT_FOUND,
                 INVALID_STATUS_TRANSITION -> HttpStatus.BAD_REQUEST;
            case DUPLICATE_ACCOUNT, DUPLICATE_PAYMENT -> HttpStatus.CONFLICT;
            case PAYMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RISK_BLOCKED -> HttpStatus.FORBIDDEN;
            case RETRY_EXHAUSTED -> HttpStatus.CONFLICT;
            case NETWORK_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROCESSING_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
