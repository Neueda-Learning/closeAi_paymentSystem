package com.hsbc.payment.controller;

import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.entity.RiskAssessment;
import com.hsbc.payment.mapper.RiskAssessmentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@Tag(name = "Risk Assessment", description = "AI-driven risk assessment and monitoring")
public class RiskController {

    private final RiskAssessmentMapper riskAssessmentMapper;

    @GetMapping("/assessments/{paymentId}")
    @Operation(summary = "Get risk assessment details for a payment")
    public ResponseEntity<ApiResponse<List<RiskAssessment>>> getPaymentRiskAssessment(@PathVariable String paymentId) {
        return ResponseEntity.ok(ApiResponse.ok(riskAssessmentMapper.findByPaymentId(paymentId)));
    }

    @GetMapping("/blocked")
    @Operation(summary = "List BLOCKED payments")
    public ResponseEntity<ApiResponse<List<RiskAssessment>>> getBlockedPayments(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(riskAssessmentMapper.findBlockedPayments(limit)));
    }

    @GetMapping("/review")
    @Operation(summary = "List REVIEW payments")
    public ResponseEntity<ApiResponse<List<RiskAssessment>>> getReviewPayments(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(riskAssessmentMapper.findReviewPayments(limit)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get risk statistics summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRiskStats() {
        long blocked = riskAssessmentMapper.countByDecision("BLOCK");
        long review = riskAssessmentMapper.countByDecision("REVIEW");
        long approve = riskAssessmentMapper.countByDecision("APPROVE");
        long todayBlocked = riskAssessmentMapper.countByDecisionSince("BLOCK",
                LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));
        long total = blocked + review + approve;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAssessments", total); stats.put("blockedCount", blocked);
        stats.put("reviewCount", review); stats.put("approveCount", approve);
        stats.put("todayBlocked", todayBlocked);
        stats.put("blockRate", total > 0 ? String.format("%.1f%%", 100.0 * blocked / total) : "N/A");
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
