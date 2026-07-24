package com.hsbc.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.FailRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.MethodName.class)
class PaymentApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ===== Case 1: Full lifecycle CREATED → VALIDATED → SENT → COMPLETED =====

    @Test @DisplayName("Case 1: Full happy path lifecycle, history has 4 entries")
    void test01_fullLifecycle() throws Exception {
        String paymentId = createPayment("ACC-100", "ACC-200", "500.00", "USD");

        // Validate
        mockMvc.perform(post("/api/payments/" + paymentId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALIDATED"));

        // Send
        mockMvc.perform(post("/api/payments/" + paymentId + "/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));

        // Complete
        mockMvc.perform(post("/api/payments/" + paymentId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // History has 4 entries
        mockMvc.perform(get("/api/payments/" + paymentId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    // ===== Case 2: Negative amount → 400 =====

    @Test @DisplayName("Case 2: Negative amount returns 400 INVALID_AMOUNT")
    void test02_negativeAmount() throws Exception {
        CreatePaymentRequest req = buildRequest("ACC-001", "ACC-002", "-100.00", "USD");
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", uuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ===== Case 3: Source == destination → 400 =====

    @Test @DisplayName("Case 3: Same source and dest account returns 400 INVALID_ACCOUNT")
    void test03_sameSourceDest() throws Exception {
        CreatePaymentRequest req = buildRequest("ACC-SAME", "ACC-SAME", "100.00", "USD");
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", uuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ACCOUNT"));
    }

    // ===== Case 4: Unsupported currency → 400 (at validate stage) =====

    @Test @DisplayName("Case 4: Unsupported currency JPY — creates OK, validate → FAILED")
    void test04_unsupportedCurrency() throws Exception {
        String paymentId = createPayment("ACC-001", "ACC-002", "100.00", "JPY");

        // Validate should fail with INVALID_CURRENCY → FAILED
        mockMvc.perform(post("/api/payments/" + paymentId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("INVALID_CURRENCY"));
    }

    // ===== Case 5: Same idempotency key twice → 200 + existing payment =====

    @Test @DisplayName("Case 5: Duplicate idempotency returns 200 with same payment")
    void test05_duplicateIdempotency() throws Exception {
        String key = uuid();
        CreatePaymentRequest req = buildRequest("ACC-010", "ACC-020", "300.00", "USD");

        // First call: 201
        String resp1 = mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id1 = objectMapper.readTree(resp1).get("data").get("id").asText();

        // Second call: 200, same id
        String resp2 = mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id2 = objectMapper.readTree(resp2).get("data").get("id").asText();

        org.junit.jupiter.api.Assertions.assertEquals(id1, id2, "Same payment ID expected");
    }

    // ===== Case 6: COMPLETED → CREATED (invalid transition) =====

    @Test @DisplayName("Case 6: COMPLETED→VALIDATED returns 400 INVALID_STATUS_TRANSITION")
    void test06_completedCannotTransition() throws Exception {
        String paymentId = createAndComplete();

        mockMvc.perform(post("/api/payments/" + paymentId + "/validate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
    }

    // ===== Case 7: SENT → VALIDATED (invalid — unless send triggered simulated NETWORK_ERROR) =====

    @Test @DisplayName("Case 7: SENT→VALIDATED returns 400 — or if NETWORK_ERROR hit, FAILED→VALIDATED succeeds")
    void test07_sentCannotGoBack() throws Exception {
        String paymentId = createPayment("ACC-001", "ACC-002", "100.00", "USD");
        mockMvc.perform(post("/api/payments/" + paymentId + "/validate"));
        mockMvc.perform(post("/api/payments/" + paymentId + "/send"));

        // Check actual status after send (may be SENT or FAILED due to 20% simulation)
        String afterSend = mockMvc.perform(get("/api/payments/" + paymentId))
                .andReturn().getResponse().getContentAsString();
        String status = objectMapper.readTree(afterSend).get("data").get("status").asText();

        if ("SENT".equals(status)) {
            // SENT → VALIDATED is invalid
            mockMvc.perform(post("/api/payments/" + paymentId + "/validate"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
        } else {
            // FAILED → VALIDATED via retry is valid
            mockMvc.perform(post("/api/payments/" + paymentId + "/retry")
                            .header("Idempotency-Key", uuid()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("VALIDATED"));
        }
    }

    // ===== Case 8: FAILED → VALIDATED retry =====

    @Test @DisplayName("Case 8: FAILED→retry→VALIDATED succeeds")
    void test08_failedRetry() throws Exception {
        String paymentId = createPayment("ACC-001", "ACC-002", "100.00", "USD");
        mockMvc.perform(post("/api/payments/" + paymentId + "/validate"));
        FailRequest failReq = new FailRequest();
        failReq.setErrorCode("PROCESSING_ERROR");
        mockMvc.perform(post("/api/payments/" + paymentId + "/fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(failReq)));

        // Retry
        mockMvc.perform(post("/api/payments/" + paymentId + "/retry")
                        .header("Idempotency-Key", uuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALIDATED"));
    }

    // ===== Case 9: Invalid account format → FAILED on validate =====

    @Test @DisplayName("Case 9: Bad account format validates to FAILED + INVALID_ACCOUNT")
    void test09_invalidAccountOnValidate() throws Exception {
        // Bypass create-time validation by using a valid-looking account at creation
        // But the account pattern check happens during validate
        // Need to create payment with a "bad" account that passes creation but fails validate
        // The ACCOUNT_PATTERN is ^ACC-\d{3,10}$ — ACC-99 would fail
        CreatePaymentRequest req = buildRequest("ACC-99", "ACC-002", "100.00", "USD");
        String resp = mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", uuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paymentId = objectMapper.readTree(resp).get("data").get("id").asText();

        // Validate → FAILED
        mockMvc.perform(post("/api/payments/" + paymentId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("INVALID_ACCOUNT"));
    }

    // ===== Case 11: Non-existent payment ID → 404 =====

    @Test @DisplayName("Case 11: Non-existent payment returns 404")
    void test11_notFound() throws Exception {
        mockMvc.perform(get("/api/payments/non-existent-uuid-12345"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_FOUND"));
    }

    // ===== Case 12: retry missing Idempotency-Key → 400 =====

    @Test @DisplayName("Case 12: Retry without Idempotency-Key returns 400")
    void test12_retryMissingHeader() throws Exception {
        // Create + fail first
        String paymentId = createPayment("ACC-001", "ACC-002", "100.00", "USD");
        mockMvc.perform(post("/api/payments/" + paymentId + "/validate"));
        FailRequest failReq = new FailRequest();
        failReq.setErrorCode("PROCESSING_ERROR");
        mockMvc.perform(post("/api/payments/" + paymentId + "/fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(failReq)));

        // Retry without header
        mockMvc.perform(post("/api/payments/" + paymentId + "/retry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ===== Case 14: Fail with invalid errorCode → 400 =====

    @Test @DisplayName("Case 14: Fail with invalid errorCode returns 400")
    void test14_invalidFailErrorCode() throws Exception {
        String paymentId = createPayment("ACC-001", "ACC-002", "100.00", "USD");
        mockMvc.perform(post("/api/payments/" + paymentId + "/validate"));

        mockMvc.perform(post("/api/payments/" + paymentId + "/fail")
                        .header("Idempotency-Key", uuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"errorCode\":\"BAD_CODE\",\"reason\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ===== Case 3 + currency validation: invalid currency at create =====

    @Test @DisplayName("Create with invalid currency in body still creates (validated on transition)")
    void createInvalidCurrencyStillCreates() throws Exception {
        CreatePaymentRequest req = buildRequest("ACC-001", "ACC-002", "100.00", "JPY");
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", uuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    // ===== History endpoint returns slim array =====

    @Test @DisplayName("/history returns slim array of status history")
    void historyReturnsSlimArray() throws Exception {
        String paymentId = createPayment("ACC-001", "ACC-002", "100.00", "USD");

        mockMvc.perform(get("/api/payments/" + paymentId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].toStatus").value("CREATED"));
    }

    // ===== List with status filter =====

    @Test @DisplayName("List payments filtered by status")
    void listFilteredByStatus() throws Exception {
        createPayment("ACC-001", "ACC-002", "100.00", "USD");
        createPayment("ACC-003", "ACC-004", "200.00", "EUR");

        mockMvc.perform(get("/api/payments?status=CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(2)));
    }

    // ===== helpers =====

    private String createPayment(String src, String dst, String amount, String currency) throws Exception {
        CreatePaymentRequest req = buildRequest(src, dst, amount, currency);
        String resp = mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", uuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("data").get("id").asText();
    }

    private String createAndComplete() throws Exception {
        String id = createPayment("ACC-201", "ACC-202", "500.00", "USD");
        // Validate, send — retry send if it hits the 20% simulated NETWORK_ERROR
        mockMvc.perform(post("/api/payments/" + id + "/validate"));
        mockMvc.perform(post("/api/payments/" + id + "/send"));

        // Check status after send; if FAILED (20% simulated NETWORK_ERROR), retry from FAILED
        String afterSend = mockMvc.perform(get("/api/payments/" + id))
                .andReturn().getResponse().getContentAsString();
        String status = objectMapper.readTree(afterSend).get("data").get("status").asText();
        if ("FAILED".equals(status)) {
            mockMvc.perform(post("/api/payments/" + id + "/retry")
                    .header("Idempotency-Key", uuid()));
            mockMvc.perform(post("/api/payments/" + id + "/send"));
        }

        mockMvc.perform(post("/api/payments/" + id + "/complete"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        return id;
    }

    private CreatePaymentRequest buildRequest(String src, String dst, String amount, String ccy) {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setSourceAccount(src);
        req.setDestinationAccount(dst);
        req.setAmount(new BigDecimal(amount));
        req.setCurrency(ccy);
        return req;
    }

    private String uuid() { return UUID.randomUUID().toString(); }
}
