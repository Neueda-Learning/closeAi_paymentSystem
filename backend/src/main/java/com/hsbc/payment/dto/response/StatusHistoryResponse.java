package com.hsbc.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StatusHistoryResponse {
    private Long id;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime changedAt;
    private String reason;
    private String errorCode;
}
