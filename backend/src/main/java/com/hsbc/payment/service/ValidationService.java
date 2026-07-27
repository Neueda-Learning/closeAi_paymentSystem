package com.hsbc.payment.service;

import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.entity.Account;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ValidationService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "CNY");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^ACC-\\d{3,10}$");

    private final AccountMapper accountMapper;

    /**
     * 创建时的基础格式校验（快速失败）：仅检查源/目标账户不能相同。
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
     * 校验失败抛出 BusinessException，由调用方 catch 后转 FAILED 状态。
     */
    public void validateOnTransition(Payment payment) {
        // 1. 账号格式校验
        if (!ACCOUNT_PATTERN.matcher(payment.getSourceAccount()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT,
                "Invalid source account format: " + payment.getSourceAccount());
        }
        if (!ACCOUNT_PATTERN.matcher(payment.getDestinationAccount()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT,
                "Invalid destination account format: " + payment.getDestinationAccount());
        }

        // 2. 货币白名单校验
        if (!SUPPORTED_CURRENCIES.contains(payment.getCurrency().toUpperCase())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENCY,
                "Currency " + payment.getCurrency() + " is not supported");
        }

        // 3. 金额边界复核
        if (payment.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || payment.getAmount().compareTo(new BigDecimal("1000000")) > 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT,
                "Amount out of valid range: " + payment.getAmount());
        }

        // 4. 源账户必须存在于系统中
        Account sourceAccount = accountMapper.selectById(payment.getSourceAccount());
        if (sourceAccount == null) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT,
                "Source account does not exist: " + payment.getSourceAccount());
        }

        // 5. 目标账户必须存在于系统中
        Account destAccount = accountMapper.selectById(payment.getDestinationAccount());
        if (destAccount == null) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT,
                "Destination account does not exist: " + payment.getDestinationAccount());
        }

        // 6. 源账户余额检查（INSUFFICIENT_FUNDS 不再死码）
        if (sourceAccount.getBalance().compareTo(payment.getAmount()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS,
                String.format("Insufficient funds: available %s %s, required %s %s",
                    sourceAccount.getBalance(), sourceAccount.getCurrency(),
                    payment.getAmount(), payment.getCurrency()));
        }
    }
}
