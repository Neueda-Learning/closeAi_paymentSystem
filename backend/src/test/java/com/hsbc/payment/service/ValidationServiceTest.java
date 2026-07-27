package com.hsbc.payment.service;

import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.entity.Account;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.AccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock AccountMapper accountMapper;
    @InjectMocks ValidationService validationService;

    @BeforeEach
    void setUp() {
        // Default: both accounts exist with enough balance (lenient — not all tests consume this)
        Account src = buildAccount("ACC-00001", "1000000.00", "USD");
        Account dst = buildAccount("ACC-00002", "5000000.00", "USD");
        lenient().when(accountMapper.selectById("ACC-00001")).thenReturn(src);
        lenient().when(accountMapper.selectById("ACC-00002")).thenReturn(dst);
    }

    // ===== validateOnCreate =====

    @Test @DisplayName("validateOnCreate: source != dest — passes")
    void onCreateDifferentAccountsPasses() {
        CreatePaymentRequest req = buildRequest("ACC-00001", "ACC-00002", "100", "USD");
        assertDoesNotThrow(() -> validationService.validateOnCreate(req));
    }

    @Test @DisplayName("validateOnCreate: source == dest — throws INVALID_ACCOUNT")
    void onCreateSameAccountsFails() {
        CreatePaymentRequest req = buildRequest("ACC-SAME", "ACC-SAME", "100", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnCreate(req));
        assertEquals(ErrorCode.INVALID_ACCOUNT, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnCreate: case-insensitive comparison")
    void onCreateCaseInsensitive() {
        CreatePaymentRequest req = buildRequest("acc-00001", "ACC-00001", "100", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnCreate(req));
        assertEquals(ErrorCode.INVALID_ACCOUNT, ex.getErrorCode());
    }

    // ===== validateOnTransition =====

    @Test @DisplayName("validateOnTransition: valid account + enough balance — passes")
    void onTransitionValid() {
        Payment p = buildPayment("ACC-00001", "ACC-00002", "100", "USD");
        assertDoesNotThrow(() -> validationService.validateOnTransition(p));
    }

    @Test @DisplayName("validateOnTransition: invalid source format — INVALID_ACCOUNT")
    void onTransitionInvalidSourceFormat() {
        Payment p = buildPayment("XXX-999", "ACC-00002", "100", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_ACCOUNT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("source account format"));
    }

    @Test @DisplayName("validateOnTransition: source account does NOT exist — INVALID_ACCOUNT")
    void onTransitionSourceNotExist() {
        Payment p = buildPayment("ACC-99999", "ACC-00002", "100", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_ACCOUNT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test @DisplayName("validateOnTransition: dest account does NOT exist — INVALID_ACCOUNT")
    void onTransitionDestNotExist() {
        Payment p = buildPayment("ACC-00001", "ACC-99999", "100", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_ACCOUNT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test @DisplayName("validateOnTransition: INSUFFICIENT_FUNDS when balance < amount")
    void onTransitionInsufficientFunds() {
        // ACC-00009 has only 5000.00 balance — 10000 > 5000 but ≤ 1,000,000
        Account lowBal = buildAccount("ACC-00009", "5000.00", "USD");
        when(accountMapper.selectById("ACC-00009")).thenReturn(lowBal);
        Payment p = buildPayment("ACC-00009", "ACC-00002", "10000", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: unsupported currency JPY — throws")
    void onTransitionUnsupportedCurrency() {
        Payment p = buildPayment("ACC-00001", "ACC-00002", "100", "JPY");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_CURRENCY, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: supported currencies pass")
    void onTransitionSupportedCurrencies() {
        for (String ccy : new String[]{"USD", "EUR", "GBP", "CNY"}) {
            Account src = buildAccount("ACC-00001", "1000000.00", ccy);
            Account dst = buildAccount("ACC-00002", "5000000.00", ccy);
            when(accountMapper.selectById("ACC-00001")).thenReturn(src);
            when(accountMapper.selectById("ACC-00002")).thenReturn(dst);
            Payment p = buildPayment("ACC-00001", "ACC-00002", "100", ccy);
            assertDoesNotThrow(() -> validationService.validateOnTransition(p));
        }
    }

    @Test @DisplayName("validateOnTransition: zero amount — INVALID_AMOUNT")
    void onTransitionZeroAmount() {
        Payment p = buildPayment("ACC-00001", "ACC-00002", "0", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_AMOUNT, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: negative amount — INVALID_AMOUNT")
    void onTransitionNegativeAmount() {
        Payment p = buildPayment("ACC-00001", "ACC-00002", "-50", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_AMOUNT, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: amount > 1,000,000 — INVALID_AMOUNT")
    void onTransitionExceedsMaxAmount() {
        Payment p = buildPayment("ACC-00001", "ACC-00002", "2000000", "USD");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validationService.validateOnTransition(p));
        assertEquals(ErrorCode.INVALID_AMOUNT, ex.getErrorCode());
    }

    @Test @DisplayName("validateOnTransition: amount = 1,000,000 passes (boundary)")
    void onTransitionMaxBoundary() {
        Account src = buildAccount("ACC-00001", "2000000.00", "USD");
        when(accountMapper.selectById("ACC-00001")).thenReturn(src);
        Payment p = buildPayment("ACC-00001", "ACC-00002", "1000000", "USD");
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

    private Account buildAccount(String number, String balance, String currency) {
        Account a = new Account();
        a.setAccountNumber(number);
        a.setBalance(new BigDecimal(balance));
        a.setCurrency(currency);
        a.setAccountName("Test Account " + number);
        return a;
    }
}
