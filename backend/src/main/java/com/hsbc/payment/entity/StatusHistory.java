package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("status_history")
public class StatusHistory {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentId;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime changedAt;
    private String reason;
    private String errorCode;
}
