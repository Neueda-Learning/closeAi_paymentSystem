package com.hsbc.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.batch.BatchPaymentRequest;
import com.hsbc.payment.dto.batch.BatchPaymentResponse;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.PaymentResponse;
import com.hsbc.payment.dto.response.StatusHistoryResponse;

import java.util.List;
import java.util.Map;

public interface PaymentService {
    // CRUD
    PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey);
    PaymentResponse getPayment(String paymentId);
    Page<PaymentResponse> listPayments(PageRequest pageRequest);
    PaymentResponse getPaymentHistory(String paymentId);
    List<StatusHistoryResponse> getStatusHistoryOnly(String paymentId);
    PaymentResponse updatePayment(String paymentId, CreatePaymentRequest request);

    // Lifecycle
    PaymentResponse processValidate(String paymentId);
    PaymentResponse processSend(String paymentId);
    PaymentResponse processComplete(String paymentId);
    PaymentResponse processFail(String paymentId, String errorCode, String reason);
    PaymentResponse processRetry(String paymentId, String idempotencyKey);

    // Advanced: Batch
    BatchPaymentResponse createBatch(BatchPaymentRequest batchRequest);

    // Advanced: Cancel & Reverse
    PaymentResponse cancelPayment(String paymentId);
    PaymentResponse reversePayment(String paymentId);

    // Advanced: Reporting
    Map<String, Object> getDailySummary();
    Map<String, Object> getSuccessRate();
    Map<String, Object> getAvgProcessingTime();
}
