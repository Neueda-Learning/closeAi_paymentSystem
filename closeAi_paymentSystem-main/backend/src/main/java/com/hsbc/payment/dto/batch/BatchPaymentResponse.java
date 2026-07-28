package com.hsbc.payment.dto.batch;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BatchPaymentResponse {
    private String batchId;
    private int totalCount;
    private int successCount;
    private int failureCount;
    private List<BatchItemResult> items;
    private LocalDateTime createdAt;
}
