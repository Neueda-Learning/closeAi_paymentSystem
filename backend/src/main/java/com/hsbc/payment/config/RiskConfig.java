package com.hsbc.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "risk")
public class RiskConfig {
    private Layer1 layer1 = new Layer1();
    private Layer2 layer2 = new Layer2();
    private Layer3 layer3 = new Layer3();
    private Thresholds thresholds = new Thresholds();

    @Data public static class Layer1 {
        private boolean enabled = true;
        private BigDecimal largeAmountBlock = new BigDecimal("1000000");
        private BigDecimal largeAmountWarning = new BigDecimal("100000");
        private int nightTimeStart = 0;
        private int nightTimeEnd = 5;
        private int selfTransferScore = 50;
        private int newPayeeScore = 30;
        private int velocityScore = 35;
    }
    @Data public static class Layer2 {
        private boolean enabled = true;
        private double zscoreThreshold = 3.0;
        private double iqrMultiplier = 1.5;
        private int velocityDeviation = 5;
    }
    @Data public static class Layer3 {
        private boolean enabled = false;
        private int timeoutSeconds = 30;
        private int maxRetries = 2;
    }
    @Data public static class Thresholds {
        private int block = 60;
        private int review = 30;
    }
}
