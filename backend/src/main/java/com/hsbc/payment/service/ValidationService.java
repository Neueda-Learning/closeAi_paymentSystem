package com.hsbc.payment.service;

import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ValidationService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "CNY");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^ACC-\\d{3,10}$");

    /**
     * 创建时的基础格式校验（快速失败）：仅检查源/目标账户不能相同。
     * 金额、货币的格式校验由 @Valid 注解 (CreatePaymentRequest) 负责。
     */
    public void validateOnCreate(CreatePaymentRequest request) {
        if (request.getSourceAccount().equalsIgnoreCase(request.getDestinationAccount())) {
            throw new BusinessException(
                ErrorCode.INVALID_ACCOUNT,
                "Source and destination accounts must be different"
            );
        }
    }

    /**
     * validate 阶段的完整业务校验（CREATED → VALIDATED 前执行）。
     * 校验失败抛出 BusinessException，由调用方（processValidate）catch 后转 FAILED 状态。
     */
    public void validateOnTransition(Payment payment) {
        // 账号格式校验
        if (!ACCOUNT_PATTERN.matcher(payment.getSourceAccount()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT,
                "Invalid source account format: " + payment.getSourceAccount());
        }
        if (!ACCOUNT_PATTERN.matcher(payment.getDestinationAccount()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT,
                "Invalid destination account format: " + payment.getDestinationAccount());
        }
        // 货币白名单校验
        if (!SUPPORTED_CURRENCIES.contains(payment.getCurrency().toUpperCase())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENCY,
                "Currency " + payment.getCurrency() + " is not supported");
        }
        // 金额边界复核（防止直接写库的脏数据流转）
        if (payment.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || payment.getAmount().compareTo(new BigDecimal("1000000")) > 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT,
                "Amount out of valid range: " + payment.getAmount());
        }
    }
}
