package com.hsbc.payment.service;

import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ValidationService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "CNY");

    public void validate(CreatePaymentRequest request) {
        // Source and destination accounts must be different
        if (request.getSourceAccount().equalsIgnoreCase(request.getDestinationAccount())) {
            throw new BusinessException(
                ErrorCode.INVALID_ACCOUNT,
                "Source and destination accounts must be different"
            );
        }

        // Currency must be supported
        if (!SUPPORTED_CURRENCIES.contains(request.getCurrency().toUpperCase())) {
            throw new BusinessException(
                ErrorCode.INVALID_CURRENCY,
                "Currency " + request.getCurrency() + " is not supported. Supported: " + SUPPORTED_CURRENCIES
            );
        }
    }
}
