package com.hsbc.payment.controller;

import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.dto.response.ExchangeRateQuoteResponse;
import com.hsbc.payment.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
@Tag(name = "Exchange Rate", description = "Currency conversion quotes")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/quote")
    @Operation(summary = "Get a conversion quote")
    public ResponseEntity<ApiResponse<ExchangeRateQuoteResponse>> quote(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new com.hsbc.payment.exception.BusinessException(
                    com.hsbc.payment.enums.ErrorCode.INVALID_AMOUNT,
                    "Quote amount must be greater than zero"
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(exchangeRateService.quote(from, to, amount)));
    }
}
