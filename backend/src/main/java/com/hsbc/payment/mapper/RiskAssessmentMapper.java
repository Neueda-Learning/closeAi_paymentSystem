package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.RiskAssessment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RiskAssessmentMapper extends BaseMapper<RiskAssessment> {

    @Select("SELECT * FROM risk_assessments WHERE payment_id = #{paymentId} ORDER BY assessed_at DESC LIMIT 1")
    RiskAssessment findLatestByPaymentId(String paymentId);

    @Select("SELECT * FROM risk_assessments WHERE payment_id = #{paymentId} ORDER BY assessed_at DESC")
    List<RiskAssessment> findByPaymentId(String paymentId);

    @Select("SELECT * FROM risk_assessments WHERE risk_decision = 'BLOCK' ORDER BY assessed_at DESC LIMIT #{limit}")
    List<RiskAssessment> findBlockedPayments(int limit);

    @Select("SELECT * FROM risk_assessments WHERE risk_decision = 'REVIEW' ORDER BY assessed_at DESC LIMIT #{limit}")
    List<RiskAssessment> findReviewPayments(int limit);

    @Select("SELECT COUNT(*) FROM risk_assessments WHERE risk_decision = #{decision}")
    long countByDecision(String decision);

    @Select("SELECT COUNT(*) FROM risk_assessments WHERE risk_decision = #{decision} AND assessed_at >= #{since}")
    long countByDecisionSince(String decision, LocalDateTime since);
}
