package com.hsbc.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.batch.BatchItemResult;
import com.hsbc.payment.dto.batch.BatchPaymentRequest;
import com.hsbc.payment.dto.batch.BatchPaymentResponse;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.PaymentResponse;
import com.hsbc.payment.dto.response.StatusHistoryResponse;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.entity.StatusHistory;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.enums.PaymentStatus;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.entity.Account;
import com.hsbc.payment.mapper.AccountMapper;
import com.hsbc.payment.mapper.PaymentMapper;
import com.hsbc.payment.mapper.RiskAssessmentMapper;
import com.hsbc.payment.mapper.StatusHistoryMapper;
import com.hsbc.payment.service.IdempotencyService;
import com.hsbc.payment.service.PaymentService;
import com.hsbc.payment.service.StateMachineService;
import com.hsbc.payment.service.ValidationService;
import com.hsbc.payment.service.risk.RiskAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_RETRIES = 3;

    private final PaymentMapper paymentMapper;
    private final StatusHistoryMapper statusHistoryMapper;
    private final StateMachineService stateMachineService;
    private final ValidationService validationService;
    private final IdempotencyService idempotencyService;
    private final com.hsbc.payment.service.risk.RiskAssessmentService riskAssessmentService;
    private final RiskAssessmentMapper riskAssessmentMapper;
    private final AccountMapper accountMapper;

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

    @Override
    @Transactional
    public PaymentResponse updatePayment(String paymentId, CreatePaymentRequest request) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus currentStatus = PaymentStatus.fromString(payment.getStatus());

        // Only allow edit in CREATED or FAILED status
        if (currentStatus != PaymentStatus.CREATED && currentStatus != PaymentStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Payment can only be edited in CREATED or FAILED status, current: " + currentStatus);
        }

        // Block edit if retry exhausted
        int retries = payment.getRetryCount() != null ? payment.getRetryCount() : 0;
        if (currentStatus == PaymentStatus.FAILED && retries >= MAX_RETRIES) {
            throw new BusinessException(ErrorCode.RETRY_EXHAUSTED,
                    "Payment retry count exhausted (" + retries + "/" + MAX_RETRIES
                    + "), cannot edit. This payment is permanently failed.");
        }

        // Update editable fields
        payment.setSourceAccount(request.getSourceAccount());
        payment.setDestinationAccount(request.getDestinationAccount());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency().toUpperCase());
        payment.setDescription(request.getDescription());

        // If FAILED, reset to CREATED and clear error
        if (currentStatus == PaymentStatus.FAILED) {
            payment.setStatus(PaymentStatus.CREATED.name());
            payment.setErrorCode(null);
            recordStatusHistory(paymentId, PaymentStatus.FAILED.name(), PaymentStatus.CREATED.name(),
                    "Payment edited, reset for re-validation", null);
        } else {
            recordStatusHistory(paymentId, PaymentStatus.CREATED.name(), PaymentStatus.CREATED.name(),
                    "Payment details updated", null);
        }

        paymentMapper.updateById(payment);
        return toPaymentResponse(payment);
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

        // ===== Risk Assessment (three-layer progressive) =====
        RiskAssessmentService.RiskAssessmentResult riskResult = riskAssessmentService.assess(payment);
        if (riskResult.riskDecision() == com.hsbc.payment.enums.RiskDecision.BLOCK) {
            updatePaymentStatus(payment, PaymentStatus.FAILED.name(), ErrorCode.RISK_BLOCKED.name());
            recordStatusHistory(paymentId, fromStatus.name(), PaymentStatus.FAILED.name(),
                    "Risk BLOCKED: score=" + riskResult.totalScore() + ", level=" + riskResult.riskLevel(),
                    ErrorCode.RISK_BLOCKED.name());
            return getPayment(paymentId);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        String riskNote = null;
        if (riskResult.riskDecision() == com.hsbc.payment.enums.RiskDecision.REVIEW)
            riskNote = "Risk REVIEW: score=" + riskResult.totalScore() + ", level=" + riskResult.riskLevel();
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), riskNote, null);
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

        // Update account balances: deduct from source, credit to destination
        Account srcAccount = accountMapper.selectById(payment.getSourceAccount());
        Account dstAccount = accountMapper.selectById(payment.getDestinationAccount());
        if (srcAccount == null || dstAccount == null) {
            throw new BusinessException(ErrorCode.PROCESSING_ERROR,
                    "Source or destination account not found during completion");
        }

        // Deduct from source
        srcAccount.setBalance(srcAccount.getBalance().subtract(payment.getAmount()));
        accountMapper.updateById(srcAccount);

        // Credit to destination (same currency — cross-currency handled separately)
        dstAccount.setBalance(dstAccount.getBalance().add(payment.getAmount()));
        accountMapper.updateById(dstAccount);

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(),
                "Completed: transferred " + payment.getAmount() + " " + payment.getCurrency()
                + " from " + payment.getSourceAccount() + " to " + payment.getDestinationAccount(),
                null);

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
                return getPayment(paymentId);
            }
            throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT,
                    "Idempotency key already used for another payment: " + idempotencyKey);
        }

        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());

        // Check retry count limit
        int currentRetries = payment.getRetryCount() != null ? payment.getRetryCount() : 0;
        if (currentRetries >= MAX_RETRIES) {
            throw new BusinessException(ErrorCode.RETRY_EXHAUSTED,
                    "Payment has been retried " + currentRetries
                    + " times (max: " + MAX_RETRIES + ") and cannot be retried further");
        }

        PaymentStatus toStatus = PaymentStatus.VALIDATED;
        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot retry from status " + fromStatus);
        }

        // Save idempotency record for this retry
        idempotencyService.checkAndSave(idempotencyKey, paymentId);

        // Increment retry count
        payment.setRetryCount(currentRetries + 1);

        // Re-validate before allowing transition to VALIDATED
        try {
            validationService.validateOnTransition(payment);
        } catch (BusinessException ex) {
            // If retry count exhausted, use RETRY_EXHAUSTED error code
            ErrorCode failCode = ex.getErrorCode();
            String reason = "Retry #" + payment.getRetryCount() + " validation failed: " + ex.getMessage();
            if (payment.getRetryCount() >= MAX_RETRIES) {
                failCode = ErrorCode.RETRY_EXHAUSTED;
                reason = "Retry exhausted after " + payment.getRetryCount() + " attempts: " + ex.getMessage();
            }
            updatePaymentStatus(payment, PaymentStatus.FAILED.name(), failCode.name());
            recordStatusHistory(paymentId, fromStatus.name(), PaymentStatus.FAILED.name(),
                    reason, failCode.name());
            return getPayment(paymentId);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(),
                "Retry attempt #" + payment.getRetryCount(), null);

        return getPayment(paymentId);
    }

    // ===== Advanced Features =====

    // --- Batch Payments ---
    @Override
    @Transactional
    public BatchPaymentResponse createBatch(BatchPaymentRequest batchRequest) {
        String batchId = UUID.randomUUID().toString();
        List<BatchItemResult> items = new ArrayList<>();
        int success = 0, failure = 0;

        for (int i = 0; i < batchRequest.getPayments().size(); i++) {
            try {
                String key = batchId + "-" + i;
                PaymentResponse resp = createPayment(batchRequest.getPayments().get(i), key);
                items.add(BatchItemResult.builder().index(i).success(true).payment(resp).build());
                success++;
            } catch (Exception e) {
                items.add(BatchItemResult.builder().index(i).success(false)
                        .errorMessage(e.getMessage()).build());
                failure++;
            }
        }

        return BatchPaymentResponse.builder()
                .batchId(batchId).totalCount(items.size())
                .successCount(success).failureCount(failure)
                .items(items).createdAt(LocalDateTime.now()).build();
    }

    // --- Cancel Payment (before COMPLETED) ---
    @Override
    @Transactional
    public PaymentResponse cancelPayment(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus current = PaymentStatus.fromString(payment.getStatus());
        if (current == PaymentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot cancel a COMPLETED payment");
        }
        updatePaymentStatus(payment, "CANCELLED", null);
        recordStatusHistory(paymentId, current.name(), "CANCELLED", "Payment cancelled by user", null);
        return getPayment(paymentId);
    }

    // --- Reverse Payment (after COMPLETED, creates offsetting payment) ---
    @Override
    @Transactional
    public PaymentResponse reversePayment(String paymentId) {
        Payment original = findPaymentById(paymentId);
        if (!"COMPLETED".equals(original.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only COMPLETED payments can be reversed");
        }
        // Mark original as REVERSED
        updatePaymentStatus(original, "REVERSED", null);
        recordStatusHistory(paymentId, "COMPLETED", "REVERSED", "Payment reversed", null);

        // Create offsetting payment
        String revKey = "REV-" + paymentId;
        Payment reversal = new Payment();
        reversal.setId(UUID.randomUUID().toString());
        reversal.setIdempotencyKey(revKey);
        reversal.setSourceAccount(original.getDestinationAccount());
        reversal.setDestinationAccount(original.getSourceAccount());
        reversal.setAmount(original.getAmount());
        reversal.setCurrency(original.getCurrency());
        reversal.setDescription("Reversal of " + paymentId);
        reversal.setStatus("CREATED");
        paymentMapper.insert(reversal);
        recordStatusHistory(reversal.getId(), null, "CREATED", "Auto-created reversal", null);

        return getPayment(paymentId);
    }

    // --- Reporting ---
    @Override
    public Map<String, Object> getDailySummary() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Payment::getCreatedAt, today);

        List<Payment> todayPayments = paymentMapper.selectList(wrapper);
        long total = todayPayments.size();
        long completed = todayPayments.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        long failed = todayPayments.stream().filter(p -> "FAILED".equals(p.getStatus())).count();
        double totalAmount = todayPayments.stream().mapToDouble(p -> p.getAmount().doubleValue()).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("date", today.toLocalDate().toString());
        summary.put("totalPayments", total);
        summary.put("completedPayments", completed);
        summary.put("failedPayments", failed);
        summary.put("totalAmount", new BigDecimal(String.format("%.2f", totalAmount)));
        summary.put("successRate", total > 0 ? String.format("%.1f%%", 100.0 * completed / total) : "N/A");
        return summary;
    }

    @Override
    public Map<String, Object> getSuccessRate() {
        List<Payment> allPayments = paymentMapper.selectList(null);
        long total = allPayments.size();
        long completed = allPayments.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        long failed = allPayments.stream().filter(p -> "FAILED".equals(p.getStatus())).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalPayments", total);
        result.put("completed", completed);
        result.put("failed", failed);
        result.put("successRate", total > 0 ? String.format("%.1f%%", 100.0 * completed / total) : "N/A");
        return result;
    }

    @Override
    public Map<String, Object> getAvgProcessingTime() {
        List<StatusHistory> histories = statusHistoryMapper.selectList(null);

        // Group by payment_id, find time from CREATED to COMPLETED
        Map<String, LocalDateTime> createdMap = new LinkedHashMap<>();
        Map<String, LocalDateTime> completedMap = new LinkedHashMap<>();

        for (StatusHistory h : histories) {
            if ("CREATED".equals(h.getToStatus())) createdMap.putIfAbsent(h.getPaymentId(), h.getChangedAt());
            if ("COMPLETED".equals(h.getToStatus())) completedMap.putIfAbsent(h.getPaymentId(), h.getChangedAt());
        }

        double totalMs = 0;
        int count = 0;
        for (String pid : completedMap.keySet()) {
            if (createdMap.containsKey(pid)) {
                totalMs += java.time.Duration.between(createdMap.get(pid), completedMap.get(pid)).toMillis();
                count++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completedCount", count);
        result.put("avgProcessingSeconds", count > 0 ? String.format("%.1f", totalMs / count / 1000.0) : "N/A");
        return result;
    }

    @Override
    public Map<String, Object> getStatusDistribution() {
        List<Payment> allPayments = paymentMapper.selectList(null);
        Map<String, Long> counts = allPayments.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStatus() != null ? p.getStatus() : "UNKNOWN",
                        Collectors.counting()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", allPayments.size());
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            result.put(e.getKey().toLowerCase(), e.getValue());
        }
        return result;
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
        com.hsbc.payment.entity.RiskAssessment latestRisk = riskAssessmentMapper != null
                ? riskAssessmentMapper.findLatestByPaymentId(payment.getId()) : null;
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
                .retryCount(payment.getRetryCount())
                .riskScore(latestRisk != null ? latestRisk.getRiskScore() : null)
                .riskLevel(latestRisk != null ? latestRisk.getRiskLevel() : null)
                .riskDecision(latestRisk != null ? latestRisk.getRiskDecision() : null)
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
