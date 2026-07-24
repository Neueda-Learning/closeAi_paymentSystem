package com.hsbc.payment.service;

import com.hsbc.payment.entity.IdempotencyRecord;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.IdempotencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyMapper idempotencyMapper;

    /**
     * Try to save an idempotency key with the given payment ID.
     * @return true if the key is new (proceed), false if duplicate exists
     */
    public boolean checkAndSave(String key, String paymentId) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setKeyRecord(key);
        record.setPaymentId(paymentId);
        try {
            idempotencyMapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /**
     * Returns the payment ID for an existing idempotency key.
     */
    public String getExistingPaymentId(String key) {
        IdempotencyRecord record = idempotencyMapper.selectById(key);
        if (record == null) {
            throw new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND,
                "No payment found for idempotency key: " + key
            );
        }
        return record.getPaymentId();
    }

    /**
     * Look up an idempotency key and return the associated payment ID.
     * Returns null if the key doesn't exist (no exception thrown).
     */
    public String findPaymentIdByKey(String key) {
        IdempotencyRecord record = idempotencyMapper.selectById(key);
        return record != null ? record.getPaymentId() : null;
    }
}
