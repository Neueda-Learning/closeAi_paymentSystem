package com.hsbc.payment.dto.batch;

import com.hsbc.payment.dto.response.PaymentResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchItemResult {
    private int index;
    private boolean success;
    private PaymentResponse payment;
    private String errorMessage;
}
