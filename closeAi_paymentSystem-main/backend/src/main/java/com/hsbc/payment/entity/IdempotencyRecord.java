package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("idempotency_keys")
public class IdempotencyRecord {
    @TableId
    private String keyRecord;

    private String paymentId;
    private LocalDateTime createdAt;
}
