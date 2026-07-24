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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final int MAX_LIMIT = 100;

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

        // 2. Basic validation on create (fast-fail: source != dest only)
        validationService.validateOnCreate(request);

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

        // 4. Save idempotency record (payment exists now)
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
    public List<StatusHistoryResponse> getStatusHistoryOnly(String paymentId) {
        // Verify payment exists
        findPaymentById(paymentId);
        return getStatusHistory(paymentId);
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
            String escaped = escapeLike(pageRequest.getKeyword());
            wrapper.and(w -> w
                .like(Payment::getId, escaped)
                .or()
                .like(Payment::getDescription, escaped)
            );
        }
        wrapper.orderByDesc(Payment::getCreatedAt);

        int limit = Math.min(pageRequest.getLimit(), MAX_LIMIT);
        Page<Payment> page = new Page<>(pageRequest.getPage(), limit);
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

    // --- State transition methods ---

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

        // Full business validation — if it fails, transition to FAILED (not throw)
        try {
            validationService.validateOnTransition(payment);
        } catch (BusinessException ex) {
            updatePaymentStatus(payment, PaymentStatus.FAILED.name(), ex.getErrorCode().name());
            recordStatusHistory(paymentId, fromStatus.name(), PaymentStatus.FAILED.name(),
                    "Validation failed: " + ex.getMessage(), ex.getErrorCode().name());
            return getPayment(paymentId);
        }

        // ★ AI risk assessment hook (Phase 4: insert riskAssessmentService.assess(payment) here)

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

        // Simulated payment gateway: 20% chance of network failure
        if (ThreadLocalRandom.current().nextInt(100) < 20) {
            updatePaymentStatus(payment, PaymentStatus.FAILED.name(), ErrorCode.NETWORK_ERROR.name());
            recordStatusHistory(paymentId, toStatus.name(), PaymentStatus.FAILED.name(),
                    "Gateway communication failed (simulated)", ErrorCode.NETWORK_ERROR.name());
        }

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
        // Idempotency check FIRST — if already retried with this key, return current state
        String existingForRetry = idempotencyService.findPaymentIdByKey(idempotencyKey);
        if (existingForRetry != null) {
            if (existingForRetry.equals(paymentId)) {
                return getPayment(paymentId);  // Same payment retry → return current state
            }
            throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT,
                    "Idempotency key already used for another payment: " + idempotencyKey);
        }

        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.VALIDATED;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot retry from status " + fromStatus);
        }

        // Save idempotency record for this retry
        idempotencyService.checkAndSave(idempotencyKey, paymentId);

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

    /**
     * Escape LIKE wildcard characters to prevent unintended pattern matching.
     */
    private String escapeLike(String keyword) {
        if (keyword == null) return null;
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
