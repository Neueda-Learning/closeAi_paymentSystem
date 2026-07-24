package com.hsbc.payment.service;

import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ValidationServiceTest {

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ValidationService();
    }

    // ===== validateOnCreate =====

    @Test @DisplayName("validateOnCreate: source != dest — passes")
    void onCreateDifferentAccountsPasses() {
        CreatePaymentRequest req = buildRequest("ACC-001", "ACC-002", "100", "USD");
        assertDoesNotThrow(() -> validationService.validateOnCreate(req));
    }

    @Test @DisplayName("validateOnCreate: source == dest — throws INVALID_ACCOUNT")
    void onCreateSameAccountsFails() {
        CreatePaymentRequest req = buildRequest("ACC-001", "ACC-001", "100", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnCreate(req));
        assertEquals(ErrorCode.INVALID_ACCOUNT, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnCreate: case-insensitive account comparison")
    void onCreateCaseInsensitive() {
        CreatePaymentRequest req = buildRequest("acc-001", "ACC-001", "100", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnCreate(req));
        assertEquals(ErrorCode.INVALID_ACCOUNT, ex.getErrorCode());
    }

    // ===== validateOnTransition =====

    @Test @DisplayName("validateOnTransition: valid account format ACC-XXX — passes")
    void onTransitionValidAccountFormat() {
        Payment p = buildPayment("ACC-001", "ACC-002", "100", "USD");
        assertDoesNotThrow(() -> validationService.validateOnTransition(p));
    }

    @Test @DisplayName("validateOnTransition: invalid source account format — throws")
    void onTransitionInvalidSourceFormat() {
        Payment p = buildPayment("XXX-999", "ACC-002", "100", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_ACCOUNT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("source account"));
    }

    @Test @DisplayName("validateOnTransition: invalid dest account format — throws")
    void onTransitionInvalidDestFormat() {
        Payment p = buildPayment("ACC-001", "BAD", "100", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_ACCOUNT, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: unsupported currency JPY — throws")
    void onTransitionUnsupportedCurrency() {
        Payment p = buildPayment("ACC-001", "ACC-002", "100", "JPY");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_CURRENCY, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: supported currencies pass")
    void onTransitionSupportedCurrencies() {
        for (String ccy : new String[]{"USD", "EUR", "GBP", "CNY"}) {
            Payment p = buildPayment("ACC-001", "ACC-002", "100", ccy);
            assertDoesNotThrow(() -> validationService.validateOnTransition(p),
                    "Currency " + ccy + " should be supported");
        }
    }

    @Test @DisplayName("validateOnTransition: zero amount — throws INVALID_AMOUNT")
    void onTransitionZeroAmount() {
        Payment p = buildPayment("ACC-001", "ACC-002", "0", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_AMOUNT, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: negative amount — throws INVALID_AMOUNT")
    void onTransitionNegativeAmount() {
        Payment p = buildPayment("ACC-001", "ACC-002", "-50", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_AMOUNT, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: amount > 1,000,000 — throws")
    void onTransitionExceedsMaxAmount() {
        Payment p = buildPayment("ACC-001", "ACC-002", "2000000", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_AMOUNT, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: amount = 1,000,000 passes (boundary)")
    void onTransitionMaxBoundary() {
        Payment p = buildPayment("ACC-001", "ACC-002", "1000000", "USD");
        assertDoesNotThrow(() -> validationService.validateOnTransition(p));
    }

    // ===== helpers =====

    private CreatePaymentRequest buildRequest(String src, String dst, String amount, String currency) {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setSourceAccount(src);
        req.setDestinationAccount(dst);
        req.setAmount(new BigDecimal(amount));
        req.setCurrency(currency);
        return req;
    }

    private Payment buildPayment(String src, String dst, String amount, String currency) {
        Payment p = new Payment();
        p.setSourceAccount(src);
        p.setDestinationAccount(dst);
        p.setAmount(new BigDecimal(amount));
        p.setCurrency(currency);
        return p;
    }
}
