package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("account_stats")
public class AccountStats {
    @TableId
    private String accountNumber;
    private BigDecimal avgAmount;
    private BigDecimal stdAmount;
    private BigDecimal medianAmount;
    private BigDecimal q1Amount;
    private BigDecimal q3Amount;
    private Integer totalCount;
    private String knownPayees;
    private LocalDateTime lastUpdated;
}
