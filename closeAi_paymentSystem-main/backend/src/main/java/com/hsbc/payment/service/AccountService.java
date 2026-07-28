package com.hsbc.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hsbc.payment.dto.request.CreateAccountRequest;
import com.hsbc.payment.dto.response.AccountResponse;
import com.hsbc.payment.entity.Account;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountMapper accountMapper;
    private final PasswordService passwordService;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        String accountNumber = request.getAccountNumber().trim().toUpperCase();
        if (accountMapper.selectById(accountNumber) != null) {
            throw duplicateAccount(accountNumber);
        }

        LocalDateTime now = LocalDateTime.now();
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setAccountName(request.getAccountName().trim());
        account.setHolderLastName(request.getHolderLastName().trim());
        account.setPasswordHash(passwordService.hash(request.getPassword()));
        account.setBalance(request.getBalance());
        account.setCurrency(request.getCurrency().trim().toUpperCase());
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        try {
            accountMapper.insert(account);
        } catch (DuplicateKeyException ex) {
            throw duplicateAccount(accountNumber);
        }
        return toResponse(account);
    }

    public List<AccountResponse> listAccounts() {
        return accountMapper.selectList(new LambdaQueryWrapper<Account>()
                        .orderByAsc(Account::getAccountNumber))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Account findAccount(String accountNumber) {
        Account account = accountMapper.selectById(accountNumber);
        if (account == null) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT,
                    "Account not found: " + accountNumber);
        }
        return account;
    }

    @Transactional
    public void updateBalance(String accountNumber, java.math.BigDecimal amountDelta) {
        Account account = accountMapper.selectById(accountNumber);
        if (account == null) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT,
                    "Account not found: " + accountNumber);
        }

        java.math.BigDecimal newBalance = account.getBalance().add(amountDelta);
        if (newBalance.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS,
                    "Insufficient balance in account: " + accountNumber);
        }

        account.setBalance(newBalance);
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);
    }

    private BusinessException duplicateAccount(String accountNumber) {
        return new BusinessException(
                ErrorCode.DUPLICATE_ACCOUNT,
                "Account already exists: " + accountNumber
        );
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .maskedAccountName(maskName(account.getAccountName()))
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) return "***";
        String trimmed = name.trim();
        if (trimmed.length() == 1) return "*";
        return trimmed.charAt(0) + "*".repeat(Math.max(1, trimmed.length() - 2))
                + trimmed.charAt(trimmed.length() - 1);
    }
}
