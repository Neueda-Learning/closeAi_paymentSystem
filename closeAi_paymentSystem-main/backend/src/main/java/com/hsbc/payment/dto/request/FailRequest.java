package com.hsbc.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FailRequest {
    @NotBlank(message = "Error code is required")
    @Pattern(
        regexp = "VALIDATION_FAILED|INSUFFICIENT_FUNDS|INVALID_ACCOUNT|INVALID_CURRENCY|INVALID_AMOUNT|PROCESSING_ERROR|NETWORK_ERROR|RISK_BLOCKED",
        message = "errorCode must be a valid ErrorCode enum value"
    )
    private String errorCode;

    private String reason;
}
