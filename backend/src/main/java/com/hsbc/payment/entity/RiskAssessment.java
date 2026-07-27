package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("risk_assessments")
public class RiskAssessment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentId;
    private Integer riskScore;
    private String riskLevel;
    private String riskDecision;
    private String triggeredRules;
    private String statisticalFlags;
    private String reasoning;
    private String llmModelUsed;
    private LocalDateTime assessedAt;
}
