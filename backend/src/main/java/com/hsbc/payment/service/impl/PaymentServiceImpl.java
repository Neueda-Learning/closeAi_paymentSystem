package com.hsbc.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.PaymentResponse;
import com.hsbc.payment.dto.response.StatusHistoryResponse;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.entity.StatusHistory;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.enums.PaymentStatus;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.PaymentMapper;
import com.hsbc.payment.mapper.StatusHistoryMapper;
import com.hsbc.payment.service.IdempotencyService;
import com.hsbc.payment.service.PaymentService;
import com.hsbc.payment.service.StateMachineService;
import com.hsbc.payment.service.ValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final StatusHistoryMapper statusHistoryMapper;
    private final StateMachineService stateMachineService;
    private final ValidationService validationService;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        // 1. Check if idempotency key already exists (SELECT only, fast path)
        String existingPaymentId = idempotencyService.findPaymentIdByKey(idempotencyKey);
        if (existingPaymentId != null) {
            return getPayment(existingPaymentId);
        }

        // 2. Business validation
        validationService.validate(request);

        // 3. Create and insert payment first
        String paymentId = UUID.randomUUID().toString();
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setSourceAccount(request.getSourceAccount());
        payment.setDestinationAccount(request.getDestinationAccount());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency().toUpperCase());
        payment.setDescription(request.getDescription());
        payment.setStatus(PaymentStatus.CREATED.name());
        paymentMapper.insert(payment);

        // 4. Save idempotency record (payment exists now, FK is satisfied)
        //    If duplicate key (concurrent race), throw — transaction rolls back,
        //    GlobalExceptionHandler returns DUPLICATE_PAYMENT error
        if (!idempotencyService.checkAndSave(idempotencyKey, paymentId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT,
                    "Duplicate idempotency key: " + idempotencyKey);
        }

        // 5. Record initial status history
        StatusHistory history = new StatusHistory();
        history.setPaymentId(paymentId);
        history.setFromStatus(null);
        history.setToStatus(PaymentStatus.CREATED.name());
        statusHistoryMapper.insert(history);

        return toPaymentResponse(payment);
    }

    @Override
    public PaymentResponse getPayment(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentResponse response = toPaymentResponse(payment);
        response.setStatusHistory(getStatusHistory(paymentId));
        return response;
    }

    @Override
    public Page<PaymentResponse> listPayments(PageRequest pageRequest) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(pageRequest.getStatus())) {
            wrapper.eq(Payment::getStatus, pageRequest.getStatus().toUpperCase());
        }
        if (StringUtils.hasText(pageRequest.getCurrency())) {
            wrapper.eq(Payment::getCurrency, pageRequest.getCurrency().toUpperCase());
        }
        if (StringUtils.hasText(pageRequest.getKeyword())) {
            wrapper.and(w -> w
                .like(Payment::getId, pageRequest.getKeyword())
                .or()
                .like(Payment::getDescription, pageRequest.getKeyword())
            );
        }
        wrapper.orderByDesc(Payment::getCreatedAt);

        Page<Payment> page = new Page<>(pageRequest.getPage(), pageRequest.getLimit());
        Page<Payment> resultPage = paymentMapper.selectPage(page, wrapper);

        Page<PaymentResponse> responsePage = new Page<>(
                resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(this::toPaymentResponse)
                .collect(Collectors.toList()));
        return responsePage;
    }

    @Override
    public PaymentResponse getPaymentHistory(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentResponse response = toPaymentResponse(payment);
        response.setStatusHistory(getStatusHistory(paymentId));
        return response;
    }

    @Override
    @Transactional
    public PaymentResponse processValidate(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.VALIDATED;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + fromStatus + " to " + toStatus);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), null, null);

        return getPayment(paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse processSend(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.SENT;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + fromStatus + " to " + toStatus);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), null, null);

        return getPayment(paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse processComplete(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.COMPLETED;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + fromStatus + " to " + toStatus);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), null, null);

        return getPayment(paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse processFail(String paymentId, String errorCode, String reason) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.FAILED;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + fromStatus + " to " + toStatus);
        }

        updatePaymentStatus(payment, toStatus.name(), errorCode);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), reason, errorCode);

        return getPayment(paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse processRetry(String paymentId, String idempotencyKey) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.VALIDATED;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot retry from status " + fromStatus);
        }

        // Check idempotency for retry
        boolean isNew = idempotencyService.checkAndSave(idempotencyKey, paymentId);
        if (!isNew) {
            throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT,
                    "Retry idempotency key already used: " + idempotencyKey);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), "Retry attempt", null);

        return getPayment(paymentId);
    }

    // --- Private helpers ---

    private Payment findPaymentById(String paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND,
                    "Payment not found: " + paymentId);
        }
        return payment;
    }

    private void updatePaymentStatus(Payment payment, String newStatus, String errorCode) {
        payment.setStatus(newStatus);
        if (errorCode != null) {
            payment.setErrorCode(errorCode);
        } else {
            payment.setErrorCode(null);
        }
        paymentMapper.updateById(payment);
    }

    private void recordStatusHistory(String paymentId, String fromStatus, String toStatus,
                                      String reason, String errorCode) {
        StatusHistory history = new StatusHistory();
        history.setPaymentId(paymentId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setReason(reason);
        history.setErrorCode(errorCode);
        statusHistoryMapper.insert(history);
    }

    private List<StatusHistoryResponse> getStatusHistory(String paymentId) {
        List<StatusHistory> histories = statusHistoryMapper.findByPaymentId(paymentId);
        return histories.stream()
                .map(h -> StatusHistoryResponse.builder()
                        .id(h.getId())
                        .fromStatus(h.getFromStatus())
                        .toStatus(h.getToStatus())
                        .changedAt(h.getChangedAt())
                        .reason(h.getReason())
                        .errorCode(h.getErrorCode())
                        .build())
                .collect(Collectors.toList());
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .sourceAccount(payment.getSourceAccount())
                .destinationAccount(payment.getDestinationAccount())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .description(payment.getDescription())
                .status(payment.getStatus())
                .errorCode(payment.getErrorCode())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
