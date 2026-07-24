package com.hsbc.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.PaymentResponse;

public interface PaymentService {
    PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey);
    PaymentResponse getPayment(String paymentId);
    Page<PaymentResponse> listPayments(PageRequest pageRequest);
    PaymentResponse getPaymentHistory(String paymentId);
    PaymentResponse processValidate(String paymentId);
    PaymentResponse processSend(String paymentId);
    PaymentResponse processComplete(String paymentId);
    PaymentResponse processFail(String paymentId, String errorCode, String reason);
    PaymentResponse processRetry(String paymentId, String idempotencyKey);
}
