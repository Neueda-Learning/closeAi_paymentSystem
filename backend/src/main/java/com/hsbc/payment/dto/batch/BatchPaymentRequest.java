package com.hsbc.payment.dto.batch;

import com.hsbc.payment.dto.request.CreatePaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchPaymentRequest {
    @NotEmpty(message = "Batch must contain at least 1 payment")
    @Size(max = 100, message = "Batch cannot exceed 100 payments")
    @Valid
    private List<CreatePaymentRequest> payments;
}
