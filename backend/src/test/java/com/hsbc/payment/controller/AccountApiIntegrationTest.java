package com.hsbc.payment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndListsAccount() throws Exception {
        String body = """
                {
                  "accountNumber": "ACC-90001",
                  "accountName": "New Customer",
                  "holderLastName": "Customer",
                  "password": "SecurePass123",
                  "balance": 1250.50,
                  "currency": "USD"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountNumber").value("ACC-90001"))
                .andExpect(jsonPath("$.data.accountName").value("New Customer"))
                .andExpect(jsonPath("$.data.balance").value(1250.50))
                .andExpect(jsonPath("$.data.currency").value("USD"));

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(11)));
    }

    @Test
    void rejectsDuplicateAccountNumber() throws Exception {
        String body = """
                {
                  "accountNumber": "ACC-00001",
                  "accountName": "Duplicate",
                  "holderLastName": "Duplicate",
                  "password": "SecurePass123",
                  "balance": 0,
                  "currency": "USD"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_ACCOUNT"));
    }

    @Test
    void validatesAccountInput() throws Exception {
        String body = """
                {
                  "accountNumber": "INVALID",
                  "accountName": "",
                  "holderLastName": "",
                  "password": "short",
                  "balance": -1,
                  "currency": "JPY"
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.accountNumber").exists())
                .andExpect(jsonPath("$.error.details.accountName").exists())
                .andExpect(jsonPath("$.error.details.balance").exists())
                .andExpect(jsonPath("$.error.details.currency").exists());
    }
}
