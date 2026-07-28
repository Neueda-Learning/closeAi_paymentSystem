package com.hsbc.payment.service.risk;

import com.hsbc.payment.enums.RiskDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 LLM 返回的纯文本为结构化决策。
 */
@Slf4j
@Component
public class AiAgentResultParser {

    private static final Pattern DECISION_PATTERN = Pattern.compile("DECISION:\\s*(REVIEW|BLOCK)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REASONING_PATTERN = Pattern.compile("REASONING:\\s*(.+?)(?=\\nCONFIDENCE:|\\nRECOMMENDED_ACTION:|$)", Pattern.DOTALL);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("CONFIDENCE:\\s*(HIGH|MEDIUM|LOW)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_PATTERN = Pattern.compile("RECOMMENDED_ACTION:\\s*(.+?)$", Pattern.DOTALL);

    public AiAgentResult parse(String llmResponse) {
        RiskDecision decision = RiskDecision.REVIEW;
        String reasoning = "", confidence = "MEDIUM", recommendedAction = "";

        Matcher dm = DECISION_PATTERN.matcher(llmResponse);
        if (dm.find()) decision = RiskDecision.fromString(dm.group(1).toUpperCase());

        Matcher rm = REASONING_PATTERN.matcher(llmResponse);
        if (rm.find()) reasoning = rm.group(1).trim();

        Matcher cm = CONFIDENCE_PATTERN.matcher(llmResponse);
        if (cm.find()) confidence = cm.group(1).toUpperCase();

        Matcher am = ACTION_PATTERN.matcher(llmResponse);
        if (am.find()) recommendedAction = am.group(1).trim();

        log.info("AI Agent parsed: decision={}, confidence={}, reasoningLength={}", decision, confidence, reasoning.length());
        return new AiAgentResult(decision, reasoning, confidence, recommendedAction, llmResponse);
    }

    public record AiAgentResult(RiskDecision decision, String reasoning, String confidence,
                                 String recommendedAction, String rawResponse) {}
}
