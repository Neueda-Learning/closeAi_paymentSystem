package com.hsbc.payment.service.risk;

import com.hsbc.payment.config.RiskConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI Risk Assessment Agent — calls OpenAI-compatible LLM API with tool-augmented context.
 *
 * Zero LangChain4j dependency. Uses Spring RestTemplate to call any
 * OpenAI-compatible API (OpenAI / DashScope / Ollama / vLLM).
 *
 * The LLM is given pre-fetched real data in the prompt (equivalent to Tool Calling
 * but simpler and more reliable). The LLM analyzes the data and returns a structured
 * risk assessment.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "risk.layer3.enabled", havingValue = "true")
public class PaymentRiskAgent {

    private final RestTemplate restTemplate;
    private final RiskConfig riskConfig;
    private final PaymentRiskTools riskTools;

    public PaymentRiskAgent(RiskConfig riskConfig, PaymentRiskTools riskTools) {
        this.riskConfig = riskConfig;
        this.riskTools = riskTools;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Assess payment risk using LLM with real data context.
     * Pre-fetches all relevant data, packages it into a prompt, and asks the LLM to analyze.
     */
    public String assessPayment(
            String paymentId, String amount, String currency,
            String sourceAccount, String destinationAccount,
            String transactionHour, String description,
            String ruleScore, String triggeredRules,
            String statScore, String statFlags
    ) {
        // Pre-fetch real data for the LLM context (equivalent to Tool Calling)
        String paymentDetails = safe(() -> riskTools.getPaymentDetails(paymentId));
        String accountProfile = safe(() -> riskTools.getAccountProfile(sourceAccount));
        String accountStats = safe(() -> riskTools.getAccountStatistics(sourceAccount));
        String recentTxns = safe(() -> riskTools.getRecentPayments(sourceAccount, 10));
        String paymentHistory = safe(() -> riskTools.getPaymentStatusHistory(paymentId));
        int recentCount = safeCount(() -> riskTools.countRecentTransactions(sourceAccount, 10));

        // Build prompt with all available data
        String prompt = String.format("""
            You are a senior payment risk analyst at a major bank. Assess this payment:

            CURRENT PAYMENT:
            - ID: %s | Amount: %s %s | Source: %s | Destination: %s | Time: %s:00
            - Description: %s

            REAL DATA FROM BACKEND SYSTEMS (pre-fetched):
            --- PAYMENT DETAILS ---
            %s
            --- ACCOUNT PROFILE ---
            %s
            --- STATISTICAL BASELINE ---
            %s
            --- RECENT TRANSACTIONS (last 10) ---
            %s
            --- PAYMENT STATUS HISTORY ---
            %s
            --- VELOCITY ---
            Recent transactions in 10min: %d

            RULE ENGINE (Layer 1): Score=%s, Rules=%s
            STATISTICAL (Layer 2): Score=%s, Flags=%s

            Based on ALL the real data above, provide your assessment in EXACTLY this format:
            DECISION: [REVIEW or BLOCK]
            REASONING: [Detailed analysis referencing specific data points and patterns]
            CONFIDENCE: [HIGH, MEDIUM, or LOW]
            RECOMMENDED_ACTION: [What action to take]

            Rules:
            - Only return BLOCK if you find strong evidence of fraud (smurfing, unusual patterns, round-trip transfers, etc.)
            - If evidence is ambiguous, return REVIEW
            - Reference specific data in your reasoning
            - Do NOT make up data — only use what's provided above
            """,
            paymentId, amount, currency, sourceAccount, destinationAccount,
            transactionHour, description,
            paymentDetails, accountProfile, accountStats, recentTxns,
            paymentHistory, recentCount, ruleScore, triggeredRules, statScore, statFlags);

        // Call LLM API (OpenAI-compatible)
        String baseUrl = System.getenv().getOrDefault("LLM_BASE_URL", "https://api.openai.com/v1");
        String apiKey = System.getenv().getOrDefault("LLM_API_KEY", "");
        String model = System.getenv().getOrDefault("LLM_MODEL_NAME", "gpt-4o-mini");

        if (apiKey.isEmpty() || "sk-demo".equals(apiKey)) {
            log.warn("LLM_API_KEY not configured — Layer 3 returning default REVIEW");
            return "DECISION: REVIEW\nREASONING: AI Agent not configured (set LLM_API_KEY env var)\nCONFIDENCE: LOW\nRECOMMENDED_ACTION: Configure LLM API key for AI-powered risk assessment";
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0.1);
            body.put("max_tokens", 1024);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
            log.info("Layer 3 LLM response: {} chars, model={}", content.length(), model);
            return content;

        } catch (Exception e) {
            log.error("Layer 3 LLM call failed: {}", e.getMessage());
            return "DECISION: REVIEW\nREASONING: LLM call failed — " + e.getMessage() + "\nCONFIDENCE: LOW\nRECOMMENDED_ACTION: Manual review";
        }
    }

    private String safe(java.util.function.Supplier<String> fn) {
        try { return fn.get(); } catch (Exception e) { return "Error: " + e.getMessage(); }
    }
    private int safeCount(java.util.function.Supplier<Integer> fn) {
        try { return fn.get(); } catch (Exception e) { return 0; }
    }
}
