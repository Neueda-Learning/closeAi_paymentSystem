package com.hsbc.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hsbc.payment.dto.response.ExchangeRateQuoteResponse;
import com.hsbc.payment.entity.ExchangeRate;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.ExchangeRateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateMapper exchangeRateMapper;

    public ExchangeRateQuoteResponse quote(String fromCurrency, String toCurrency, BigDecimal amount) {
        String from = fromCurrency.toUpperCase();
        String to = toCurrency.toUpperCase();
        BigDecimal rate;
        java.time.LocalDateTime updatedAt = null;

        if (from.equals(to)) {
            rate = BigDecimal.ONE;
        } else {
            ExchangeRate exchangeRate = exchangeRateMapper.selectOne(
                    new LambdaQueryWrapper<ExchangeRate>()
                            .eq(ExchangeRate::getFromCurrency, from)
                            .eq(ExchangeRate::getToCurrency, to)
                            .last("LIMIT 1")
            );
            if (exchangeRate == null) {
                throw new BusinessException(
                        ErrorCode.EXCHANGE_RATE_NOT_FOUND,
                        "Exchange rate is unavailable for " + from + "/" + to
                );
            }
            rate = exchangeRate.getRate();
            updatedAt = exchangeRate.getUpdatedAt();
        }

        return ExchangeRateQuoteResponse.builder()
                .fromCurrency(from)
                .toCurrency(to)
                .sourceAmount(amount)
                .rate(rate)
                .settlementAmount(amount.multiply(rate).setScale(2, RoundingMode.HALF_UP))
                .rateUpdatedAt(updatedAt)
                .build();
    }
}
