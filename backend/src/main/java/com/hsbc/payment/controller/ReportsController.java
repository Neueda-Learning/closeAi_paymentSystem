package com.hsbc.payment.controller;

import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Payment analytics and reporting")
public class ReportsController {

    private final PaymentService paymentService;

    @GetMapping("/daily-summary")
    @Operation(summary = "Get today's payment summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dailySummary() {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getDailySummary()));
    }

    @GetMapping("/success-rate")
    @Operation(summary = "Get overall success/failure rate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> successRate() {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getSuccessRate()));
    }

    @GetMapping("/avg-processing-time")
    @Operation(summary = "Get average processing time for completed payments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> avgProcessingTime() {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getAvgProcessingTime()));
    }
}
