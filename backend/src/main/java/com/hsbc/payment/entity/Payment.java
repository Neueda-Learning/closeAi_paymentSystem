package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payments")
public class Payment {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String idempotencyKey;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
