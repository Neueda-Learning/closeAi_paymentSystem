package com.hsbc.payment.controller;

import com.hsbc.payment.dto.request.FailRequest;
import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.dto.response.PaymentResponse;
import com.hsbc.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/{id}")
@RequiredArgsConstructor
@Tag(name = "Payment Process", description = "Payment state transition operations")
public class PaymentProcessController {

    private final PaymentService paymentService;

    @PostMapping("/validate")
    @Operation(summary = "Validate payment (CREATED → VALIDATED)")
    public ResponseEntity<ApiResponse<PaymentResponse>> validate(@PathVariable String id) {
        PaymentResponse response = paymentService.processValidate(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/send")
    @Operation(summary = "Send payment (VALIDATED → SENT)")
    public ResponseEntity<ApiResponse<PaymentResponse>> send(@PathVariable String id) {
        PaymentResponse response = paymentService.processSend(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/complete")
    @Operation(summary = "Complete payment (SENT → COMPLETED)")
    public ResponseEntity<ApiResponse<PaymentResponse>> complete(@PathVariable String id) {
        PaymentResponse response = paymentService.processComplete(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/fail")
    @Operation(summary = "Mark payment as failed")
    public ResponseEntity<ApiResponse<PaymentResponse>> fail(
            @PathVariable String id,
            @Valid @RequestBody FailRequest request) {
        PaymentResponse response = paymentService.processFail(id,
                request.getErrorCode(), request.getReason());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/retry")
    @Operation(summary = "Retry a failed payment (FAILED → VALIDATED)")
    public ResponseEntity<ApiResponse<PaymentResponse>> retry(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey) {
        PaymentResponse response = paymentService.processRetry(id, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
