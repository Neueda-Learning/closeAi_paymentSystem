package com.hsbc.payment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.dto.response.ErrorResponse;
import com.hsbc.payment.dto.response.PaymentResponse;
import com.hsbc.payment.enums.ErrorCode;
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

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment CRUD and query operations")
public class PaymentController {

    private final PaymentService paymentService;

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

        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
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

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID with full details and status history")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable String id) {
        PaymentResponse response = paymentService.getPayment(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get payment status change history")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentHistory(@PathVariable String id) {
        PaymentResponse response = paymentService.getPaymentHistory(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
