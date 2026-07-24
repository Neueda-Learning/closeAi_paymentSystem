package com.hsbc.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FailRequest {
    @NotBlank(message = "Error code is required")
    private String errorCode;

    private String reason;
}
