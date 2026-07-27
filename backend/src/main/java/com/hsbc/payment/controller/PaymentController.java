package com.hsbc.payment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.dto.response.ErrorResponse;
import com.hsbc.payment.dto.response.PaymentResponse;
import com.hsbc.payment.dto.response.StatusHistoryResponse;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.service.IdempotencyService;
import com.hsbc.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment CRUD and query operations")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    @Operation(summary = "Create a new payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @Parameter(description = "Client-generated idempotency key (UUID)")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        if (!StringUtils.hasText(idempotencyKey)) {
            ErrorResponse error = ErrorResponse.builder()
                    .code(ErrorCode.VALIDATION_FAILED.name())
                    .message("Idempotency-Key header is required")
                    .build();
            return ResponseEntity.badRequest().body(ApiResponse.fail(error));
        }

        // Check if key already exists before calling service
        boolean isDuplicate = idempotencyService.findPaymentIdByKey(idempotencyKey) != null;
        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);
        HttpStatus status = isDuplicate ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "List payments with optional filtering")
    public ResponseEntity<ApiResponse<?>> listPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {

        PageRequest pageRequest = new PageRequest();
        pageRequest.setStatus(status);
        pageRequest.setCurrency(currency);
        pageRequest.setKeyword(keyword);
        pageRequest.setPage(page);
        pageRequest.setLimit(limit);

        Page<PaymentResponse> result = paymentService.listPayments(pageRequest);
        return ResponseEntity.ok(ApiResponse.ok(result.getRecords(), result.getTotal()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update payment details (only from CREATED or FAILED status)")
    public ResponseEntity<ApiResponse<PaymentResponse>> updatePayment(
            @PathVariable String id,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse response = paymentService.updatePayment(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID with full details and status history")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable String id) {
        PaymentResponse response = paymentService.getPayment(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get payment status change history")
    public ResponseEntity<ApiResponse<List<StatusHistoryResponse>>> getPaymentHistory(@PathVariable String id) {
        List<StatusHistoryResponse> history = paymentService.getStatusHistoryOnly(id);
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
