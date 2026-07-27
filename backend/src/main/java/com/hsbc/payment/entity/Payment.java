package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payments")
public class Payment {
    @TableId(type = IdType.INPUT)
    private String id;

    private String idempotencyKey;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorCode;

    private Integer retryCount;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
