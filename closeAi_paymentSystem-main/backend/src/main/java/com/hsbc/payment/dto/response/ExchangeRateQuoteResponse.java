package com.hsbc.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExchangeRateQuoteResponse {
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal sourceAmount;
    private BigDecimal rate;
    private BigDecimal settlementAmount;
    private LocalDateTime rateUpdatedAt;
}
