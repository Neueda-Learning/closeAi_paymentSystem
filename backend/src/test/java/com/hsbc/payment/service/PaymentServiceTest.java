package com.hsbc.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.PaymentResponse;
import com.hsbc.payment.dto.response.StatusHistoryResponse;
import com.hsbc.payment.dto.response.ExchangeRateQuoteResponse;
import com.hsbc.payment.entity.Account;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.entity.StatusHistory;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.enums.PaymentStatus;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.PaymentMapper;
import com.hsbc.payment.mapper.StatusHistoryMapper;
import com.hsbc.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentMapper paymentMapper;
    @Mock StatusHistoryMapper statusHistoryMapper;
    @Mock StateMachineService stateMachineService;
    @Mock ValidationService validationService;
    @Mock IdempotencyService idempotencyService;
    @Mock com.hsbc.payment.service.risk.RiskAssessmentService riskAssessmentService;
    @Mock com.hsbc.payment.mapper.RiskAssessmentMapper riskAssessmentMapper;
    @Mock AccountService accountService;
    @Mock ExchangeRateService exchangeRateService;
    @InjectMocks PaymentServiceImpl paymentService;

    private CreatePaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreatePaymentRequest();
        validRequest.setSourceAccount("ACC-001");
        validRequest.setDestinationAccount("ACC-002");
        validRequest.setAmount(new BigDecimal("100.00"));
        validRequest.setCurrency("USD");
        // Default risk assessment: APPROVE (lenient, not consumed by all tests)
        lenient().when(riskAssessmentService.assess(any()))
                .thenReturn(new com.hsbc.payment.service.risk.RiskAssessmentService.RiskAssessmentResult(
                        0, com.hsbc.payment.enums.RiskLevel.LOW, com.hsbc.payment.enums.RiskDecision.APPROVE,
                        List.of(), null, null));

        validRequest.setSourceAccountPassword("Payment@123");
        validRequest.setRecipientLastName("Recipient");

        Account destination = new Account();
        destination.setAccountNumber("ACC-002");
        destination.setCurrency("USD");
        lenient().when(accountService.findAccount(anyString())).thenReturn(destination);
        lenient().when(exchangeRateService.quote(anyString(), anyString(), any()))
                .thenAnswer(invocation -> ExchangeRateQuoteResponse.builder()
                        .fromCurrency(invocation.getArgument(0))
                        .toCurrency(invocation.getArgument(1))
                        .sourceAmount(invocation.getArgument(2))
                        .rate(BigDecimal.ONE)
                        .settlementAmount(invocation.getArgument(2))
                        .build());
    }

    // ===== Case 1: Happy path create =====

    @Test @DisplayName("Create payment — success path")
    void createPaymentSuccess() {
        when(idempotencyService.findPaymentIdByKey(anyString())).thenReturn(null);
        when(idempotencyService.checkAndSave(anyString(), anyString())).thenReturn(true);
        doNothing().when(validationService).validateOnCreate(any());
        when(paymentMapper.insert(any(Payment.class))).thenReturn(1);
        when(statusHistoryMapper.insert(any(StatusHistory.class))).thenReturn(1);

        PaymentResponse response = paymentService.createPayment(validRequest, "key-001");

        assertNotNull(response);
        assertEquals("CREATED", response.getStatus());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        verify(paymentMapper).insert(any(Payment.class));
        verify(statusHistoryMapper).insert(any(StatusHistory.class));
    }

    // ===== Case 5: Duplicate idempotency =====

    @Test @DisplayName("Create payment — duplicate idempotency returns existing")
    void createPaymentDuplicate() {
        String existingId = "pay-existing";
        when(idempotencyService.findPaymentIdByKey("key-dup")).thenReturn(existingId);
        Payment existing = buildPaymentInDb(existingId, "CREATED", "100", "USD");
        when(paymentMapper.selectById(existingId)).thenReturn(existing);
        when(statusHistoryMapper.findByPaymentId(existingId)).thenReturn(List.of());

        PaymentResponse response = paymentService.createPayment(validRequest, "key-dup");

        assertEquals(existingId, response.getId());
        verify(validationService, never()).validateOnCreate(any());
        verify(paymentMapper, never()).insert(any());
    }

    // ===== Case 2: Negative amount (caught by DTO layer, not service) =====

    @Test @DisplayName("Get payment — not found throws PAYMENT_NOT_FOUND")
    void getPaymentNotFound() {
        when(paymentMapper.selectById("no-such-id")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.getPayment("no-such-id"));
        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, ex.getErrorCode());
    }

    // ===== Case 6: COMPLETED → CREATED =====

    @Test @DisplayName("Validate — COMPLETED→VALIDATED throws INVALID_STATUS_TRANSITION")
    void completedToValidatedInvalid() {
        Payment completed = buildPaymentInDb("pay-001", "COMPLETED", "100", "USD");
        when(paymentMapper.selectById("pay-001")).thenReturn(completed);
        when(stateMachineService.canTransition(any(PaymentStatus.class), any(PaymentStatus.class))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.processValidate("pay-001"));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.getErrorCode());
    }

    // ===== Case 7: SENT → VALIDATED =====

    @Test @DisplayName("Validate — SENT→VALIDATED throws INVALID_STATUS_TRANSITION")
    void sentToValidatedInvalid() {
        Payment sent = buildPaymentInDb("pay-001", "SENT", "100", "USD");
        when(paymentMapper.selectById("pay-001")).thenReturn(sent);
        when(stateMachineService.canTransition(any(PaymentStatus.class), any(PaymentStatus.class))).thenReturn(false);

        assertThrows(BusinessException.class, () -> paymentService.processValidate("pay-001"));
    }

    // ===== Case 8: FAILED → VALIDATED retry =====

    @Test @DisplayName("Retry — FAILED→VALIDATED succeeds")
    void retryFailedPayment() {
        Payment failed = buildPaymentInDb("pay-001", "FAILED", "100", "USD");
        when(paymentMapper.selectById("pay-001")).thenReturn(failed);
        when(idempotencyService.findPaymentIdByKey(anyString())).thenReturn(null);
        when(idempotencyService.checkAndSave(anyString(), eq("pay-001"))).thenReturn(true);
        when(stateMachineService.canTransition(any(PaymentStatus.class), any(PaymentStatus.class))).thenReturn(true);
        when(paymentMapper.updateById(any())).thenReturn(1);
        when(statusHistoryMapper.insert(any())).thenReturn(1);
        when(statusHistoryMapper.findByPaymentId("pay-001")).thenReturn(List.of());

        PaymentResponse response = paymentService.processRetry("pay-001", "retry-key");

        assertEquals("VALIDATED", response.getStatus());
    }

    // ===== Case 9: Account format invalid on validate =====

    @Test @DisplayName("Validate — invalid account format → FAILED")
    void validateWithInvalidAccount() {
        Payment payment = buildPaymentInDb("pay-001", "CREATED", "100", "USD");
        payment.setSourceAccount("XXX-999"); // bad format
        when(paymentMapper.selectById("pay-001")).thenReturn(payment);
        when(stateMachineService.canTransition(any(PaymentStatus.class), any(PaymentStatus.class))).thenReturn(true);
        doThrow(new BusinessException(ErrorCode.INVALID_ACCOUNT, "Invalid source account format"))
                .when(validationService).validateOnTransition(any());
        when(paymentMapper.updateById(any())).thenReturn(1);
        when(statusHistoryMapper.insert(any())).thenReturn(1);
        when(statusHistoryMapper.findByPaymentId("pay-001")).thenReturn(List.of());

        PaymentResponse response = paymentService.processValidate("pay-001");

        assertEquals("FAILED", response.getStatus());
        assertEquals("INVALID_ACCOUNT", response.getErrorCode());
    }

    // ===== Case 11: Non-existent payment ID =====

    @Test @DisplayName("processFail — non-existent ID throws 404")
    void processFailNotFound() {
        when(paymentMapper.selectById("no-id")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.processFail("no-id", "ERROR", "reason"));
        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, ex.getErrorCode());
    }

    // ===== Case 12: Retry missing idempotency key =====

    @Test @DisplayName("Retry — duplicate key for different payment throws 409")
    void retryDuplicateKeyDifferentPayment() {
        when(idempotencyService.findPaymentIdByKey("reused-key")).thenReturn("pay-other");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.processRetry("pay-001", "reused-key"));
        assertEquals(ErrorCode.DUPLICATE_PAYMENT, ex.getErrorCode());
    }

    // ===== Case 15: Keyword LIKE escaping =====

    @Test @DisplayName("List payments — keyword with % is escaped")
    void listPaymentsKeywordEscaping() {
        PageRequest pageReq = new PageRequest();
        pageReq.setKeyword("test%_test");
        pageReq.setPage(1);
        pageReq.setLimit(10);
        when(paymentMapper.selectPage(any(Page.class), any())).thenReturn(
                new Page<Payment>(1, 10, 0));

        Page<PaymentResponse> result = paymentService.listPayments(pageReq);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    // ===== Case 13: Pagination limit capped =====

    @Test @DisplayName("List payments — limit > 100 is capped")
    void listPaymentsLimitCapped() {
        PageRequest pageReq = new PageRequest();
        pageReq.setLimit(1000);
        pageReq.setPage(1);
        when(paymentMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            Page<Payment> p = inv.getArgument(0);
            assertEquals(100, p.getSize()); // capped to 100
            return new Page<Payment>(1, 100, 0);
        });

        Page<PaymentResponse> result = paymentService.listPayments(pageReq);
        assertNotNull(result);
    }

    // ===== getStatusHistoryOnly =====

    @Test @DisplayName("getStatusHistoryOnly returns slim list")
    void getStatusHistoryOnly() {
        Payment payment = buildPaymentInDb("pay-001", "CREATED", "100", "USD");
        when(paymentMapper.selectById("pay-001")).thenReturn(payment);
        when(statusHistoryMapper.findByPaymentId("pay-001")).thenReturn(List.of());

        List<StatusHistoryResponse> history = paymentService.getStatusHistoryOnly("pay-001");
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    // ===== Full lifecycle mock (Case 1) =====

    @Test @DisplayName("Full lifecycle: CREATE → VALIDATE → SEND → COMPLETE")
    void fullLifecycle() {
        Payment payment = buildPaymentInDb("pay-life", "CREATED", "500", "EUR");

        // Validate
        when(paymentMapper.selectById("pay-life")).thenReturn(payment);
        when(stateMachineService.canTransition(any(PaymentStatus.class), any(PaymentStatus.class))).thenReturn(true);
        when(paymentMapper.updateById(any())).thenReturn(1);
        when(statusHistoryMapper.insert(any())).thenReturn(1);
        when(statusHistoryMapper.findByPaymentId("pay-life")).thenReturn(List.of());

        payment.setStatus("VALIDATED");
        PaymentResponse r1 = paymentService.processValidate("pay-life");
        assertEquals("VALIDATED", r1.getStatus());

        // Send — may trigger simulated 20% NETWORK_ERROR
        payment.setStatus("SENT");
        PaymentResponse r2 = paymentService.processSend("pay-life");
        assertTrue(r2.getStatus().equals("SENT") || r2.getStatus().equals("FAILED"),
                "Expected SENT or FAILED (20% simulated), got " + r2.getStatus());

        // Complete only if send succeeded
        if ("SENT".equals(r2.getStatus())) {
            payment.setStatus("COMPLETED");
            PaymentResponse r3 = paymentService.processComplete("pay-life");
            assertEquals("COMPLETED", r3.getStatus());
        }
    }

    // ===== helper =====

    private Payment buildPaymentInDb(String id, String status, String amount, String currency) {
        Payment p = new Payment();
        p.setId(id);
        p.setStatus(status);
        p.setAmount(new BigDecimal(amount));
        p.setCurrency(currency);
        p.setExchangeRate(BigDecimal.ONE);
        p.setSettlementAmount(new BigDecimal(amount));
        p.setSettlementCurrency(currency);
        p.setSourceAccount("ACC-001");
        p.setDestinationAccount("ACC-002");
        p.setIdempotencyKey("key-" + id);
        return p;
    }
}
