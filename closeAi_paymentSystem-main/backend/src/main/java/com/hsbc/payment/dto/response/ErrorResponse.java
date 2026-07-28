package com.hsbc.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private String code;
    private String message;
    private Map<String, Object> details;
}
