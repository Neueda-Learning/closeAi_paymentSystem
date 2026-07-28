package com.hsbc.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PaymentResponse {
    private String id;
    private String idempotencyKey;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal settlementAmount;
    private String settlementCurrency;
    private String description;
    private String status;
    private String errorCode;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<StatusHistoryResponse> statusHistory;

    // AI risk assessment fields (Phase 4)
    private Integer riskScore;
    private String riskLevel;
    private String riskDecision;
}
