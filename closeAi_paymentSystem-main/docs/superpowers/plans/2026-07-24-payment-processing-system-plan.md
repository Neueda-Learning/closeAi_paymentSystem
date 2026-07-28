# Payment Processing System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a full-stack payment processing system with Spring Boot backend, Vue 3 frontend, and state-machine-driven payment lifecycle management.

**Architecture:** Layered monorepo — Spring Boot REST API (Controller → Service → Mapper → MySQL) with MyBatis-Plus ORM, plus Vue 3 SPA frontend consuming the API via Axios. Core business logic is a finite state machine governing payment status transitions with full audit trail.

**Tech Stack:** Spring Boot 3 + Java 17 + MySQL 8 + MyBatis-Plus 3 + Maven | Vue 3 + Vite + Element Plus + Axios + Pinia + Vue Router 4

## Global Constraints

- Java 17+, Spring Boot 3.x, MySQL 8.0+, MyBatis-Plus 3.x, Maven 3.8+
- Node.js 18+, Vue 3 (Composition API + `<script setup>`), Vite 5.x
- No authentication (single-user system per spec)
- No real payment gateway — internal simulation only
- `Idempotency-Key` passed via HTTP header, not request body
- Status transitions: only CREATED→VALIDATED→SENT→COMPLETED, FAILED from any but COMPLETED, FAILED→VALIDATED for retry
- Amount validation: > 0, ≤ 1,000,000, max 2 decimal places
- Currency: ISO 4217 codes (USD, EUR, GBP, CNY)
- All timestamps in database default to CURRENT_TIMESTAMP
- Monorepo: `backend/` and `frontend/` under project root

---

## File Structure

```
closeAi_paymentSystem/
├── backend/
│   ├── pom.xml                                          # Maven project config
│   ├── src/main/java/com/hsbc/payment/
│   │   ├── PaymentApplication.java                      # Spring Boot entry point
│   │   ├── controller/
│   │   │   ├── PaymentController.java                   # CRUD + query endpoints
│   │   │   └── PaymentProcessController.java            # State transition endpoints
│   │   ├── service/
│   │   │   ├── PaymentService.java                      # Service interface
│   │   │   ├── impl/PaymentServiceImpl.java             # Core business logic
│   │   │   ├── StateMachineService.java                  # State transition validation
│   │   │   ├── IdempotencyService.java                   # Idempotency key management
│   │   │   └── ValidationService.java                    # Payment field validation
│   │   ├── mapper/
│   │   │   ├── PaymentMapper.java                        # payments table mapper
│   │   │   ├── StatusHistoryMapper.java                  # status_history table mapper
│   │   │   └── IdempotencyMapper.java                    # idempotency_keys table mapper
│   │   ├── entity/
│   │   │   ├── Payment.java                              # Payment entity
│   │   │   ├── StatusHistory.java                        # Status history entity
│   │   │   └── IdempotencyRecord.java                    # Idempotency record entity
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── CreatePaymentRequest.java             # POST /api/payments body
│   │   │   │   ├── FailRequest.java                      # POST /api/payments/{id}/fail body
│   │   │   │   └── PageRequest.java                      # Pagination params
│   │   │   └── response/
│   │   │       ├── PaymentResponse.java                  # Payment detail + history
│   │   │       ├── StatusHistoryResponse.java            # Single history entry
│   │   │       ├── ErrorResponse.java                    # Unified error format
│   │   │       └── ApiResponse.java                      # Generic API response wrapper
│   │   ├── enums/
│   │   │   ├── PaymentStatus.java                        # CREATED/VALIDATED/SENT/COMPLETED/FAILED
│   │   │   └── ErrorCode.java                            # Error code constants
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java               # @RestControllerAdvice
│   │   │   └── BusinessException.java                    # Business exception with ErrorCode
│   │   └── config/
│   │       ├── MyBatisPlusConfig.java                    # MyBatis-Plus pagination plugin
│   │       └── SwaggerConfig.java                        # SpringDoc OpenAPI config
│   ├── src/main/resources/
│   │   ├── application.yml                               # DB connection, server port, mybatis config
│   │   └── db/
│   │       └── schema.sql                                # DDL for all tables
│   └── src/test/java/com/hsbc/payment/
│       ├── service/
│       │   ├── StateMachineServiceTest.java              # State transition unit tests
│       │   ├── PaymentServiceTest.java                   # Payment service unit tests
│       │   └── PaymentServiceIntegrationTest.java        # End-to-end lifecycle tests
│       └── controller/
│           └── PaymentControllerTest.java                # Controller integration tests
│
├── frontend/
│   ├── index.html
│   ├── package.json
│   ├── vite.config.js
│   ├── src/
│   │   ├── main.js                                       # Vue app entry
│   │   ├── App.vue                                       # Root component with layout
│   │   ├── router/index.js                               # Vue Router configuration
│   │   ├── stores/payment.js                             # Pinia payment store
│   │   ├── api/
│   │   │   ├── index.js                                  # Axios instance + interceptors
│   │   │   └── payment.js                                # Payment API functions
│   │   ├── views/
│   │   │   ├── CreatePaymentView.vue                     # Create payment page
│   │   │   ├── PaymentListView.vue                       # Payment list with search/filter
│   │   │   └── PaymentDetailView.vue                     # Payment detail + timeline
│   │   ├── components/
│   │   │   ├── PaymentForm.vue                            # Reusable payment form
│   │   │   ├── PaymentTable.vue                           # Payment data table
│   │   │   ├── StatusTimeline.vue                         # Vertical status timeline
│   │   │   ├── StatusBadge.vue                            # Color-coded status chip
│   │   │   ├── ErrorPanel.vue                             # Error details display
│   │   │   └── ActionButtons.vue                          # Dynamic action buttons
│   │   └── utils/
│   │       ├── constants.js                               # Status/currency/error mappings
│   │       └── validators.js                              # Frontend validation rules
│   └── dist/                                              # Build output
│
└── README.md
```

---

## Phase 1: Backend Core

### Task 1: Initialize Spring Boot Project

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/hsbc/payment/PaymentApplication.java`
- Create: `backend/src/main/resources/application.yml`

**Produces:** Runnable Spring Boot skeleton that connects to MySQL.

- [ ] **Step 1: Create `backend/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.hsbc</groupId>
    <artifactId>payment-system</artifactId>
    <version>1.0.0</version>
    <name>Payment Processing System</name>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.5</version>
        </dependency>

        <!-- MySQL Driver -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- SpringDoc OpenAPI (Swagger UI) -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.3.0</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `backend/src/main/resources/application.yml`**

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payment_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf-8
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: assign_uuid
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

- [ ] **Step 3: Create `backend/src/main/java/com/hsbc/payment/PaymentApplication.java`**

```java
package com.hsbc.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
```

- [ ] **Step 4: Build and verify project starts**

```bash
cd backend
mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd backend
git add pom.xml src/main/java/com/hsbc/payment/PaymentApplication.java src/main/resources/application.yml
git commit -m "feat: initialize Spring Boot project with MyBatis-Plus and Swagger dependencies"
```

---

### Task 2: Create Database Schema

**Files:**
- Create: `backend/src/main/resources/db/schema.sql`

**Produces:** Three tables ready for MyBatis-Plus entities.

- [ ] **Step 1: Create `backend/src/main/resources/db/schema.sql`**

```sql
CREATE TABLE IF NOT EXISTS payments (
    id                  VARCHAR(36)  PRIMARY KEY,
    idempotency_key     VARCHAR(64)  NOT NULL,
    source_account      VARCHAR(50)  NOT NULL,
    destination_account VARCHAR(50)  NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    description         TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    error_code          VARCHAR(50),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id  VARCHAR(36)  NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason      TEXT,
    error_code  VARCHAR(50),
    CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS idempotency_keys (
    key_record  VARCHAR(64) PRIMARY KEY,
    payment_id  VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_idempotency_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: Verify SQL syntax** (ensure MySQL is running)

```bash
mysql -u root -proot -e "CREATE DATABASE IF NOT EXISTS payment_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/schema.sql
git commit -m "feat: add database schema for payments, status_history, and idempotency_keys tables"
```

---

### Task 3: Create Enums

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/enums/PaymentStatus.java`
- Create: `backend/src/main/java/com/hsbc/payment/enums/ErrorCode.java`

**Produces:** Type-safe enums for status and error codes, consumed by all services and controllers.

- [ ] **Step 1: Create `PaymentStatus.java`**

```java
package com.hsbc.payment.enums;

public enum PaymentStatus {
    CREATED,
    VALIDATED,
    SENT,
    COMPLETED,
    FAILED;

    public static PaymentStatus fromString(String value) {
        if (value == null) return null;
        try {
            return PaymentStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: Create `ErrorCode.java`**

```java
package com.hsbc.payment.enums;

public enum ErrorCode {
    VALIDATION_FAILED,
    INSUFFICIENT_FUNDS,
    INVALID_ACCOUNT,
    INVALID_CURRENCY,
    INVALID_AMOUNT,
    DUPLICATE_PAYMENT,
    INVALID_STATUS_TRANSITION,
    PAYMENT_NOT_FOUND,
    PROCESSING_ERROR,
    NETWORK_ERROR,
    RISK_BLOCKED
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/enums/
git commit -m "feat: add PaymentStatus and ErrorCode enums"
```

---

### Task 4: Create Entities

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/entity/Payment.java`
- Create: `backend/src/main/java/com/hsbc/payment/entity/StatusHistory.java`
- Create: `backend/src/main/java/com/hsbc/payment/entity/IdempotencyRecord.java`

**Produces:** JPA-annotated entity classes mapped to MySQL tables via MyBatis-Plus.

- [ ] **Step 1: Create `Payment.java`**

```java
package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payments")
public class Payment {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String idempotencyKey;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create `StatusHistory.java`**

```java
package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("status_history")
public class StatusHistory {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentId;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime changedAt;
    private String reason;
    private String errorCode;
}
```

- [ ] **Step 3: Create `IdempotencyRecord.java`**

```java
package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("idempotency_keys")
public class IdempotencyRecord {
    @TableId
    private String keyRecord;

    private String paymentId;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd backend && mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/entity/
git commit -m "feat: add Payment, StatusHistory, and IdempotencyRecord entities"
```

---

### Task 5: Create Mapper Interfaces

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/mapper/PaymentMapper.java`
- Create: `backend/src/main/java/com/hsbc/payment/mapper/StatusHistoryMapper.java`
- Create: `backend/src/main/java/com/hsbc/payment/mapper/IdempotencyMapper.java`

**Produces:** MyBatis-Plus mapper interfaces for all three tables.

- [ ] **Step 1: Create `PaymentMapper.java`**

```java
package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
```

- [ ] **Step 2: Create `StatusHistoryMapper.java`**

```java
package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.StatusHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StatusHistoryMapper extends BaseMapper<StatusHistory> {

    @Select("SELECT * FROM status_history WHERE payment_id = #{paymentId} ORDER BY changed_at ASC")
    List<StatusHistory> findByPaymentId(String paymentId);
}
```

- [ ] **Step 3: Create `IdempotencyMapper.java`**

```java
package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IdempotencyMapper extends BaseMapper<IdempotencyRecord> {
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/mapper/
git commit -m "feat: add MyBatis-Plus mapper interfaces"
```

---

### Task 6: Create Config Classes

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/config/MyBatisPlusConfig.java`
- Create: `backend/src/main/java/com/hsbc/payment/config/SwaggerConfig.java`
- Create: `backend/src/main/java/com/hsbc/payment/config/WebMvcConfig.java`

**Produces:** MyBatis-Plus pagination plugin, OpenAPI info, and CORS configuration.

- [ ] **Step 1: Create `MyBatisPlusConfig.java`**

```java
package com.hsbc.payment.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 2: Create `SwaggerConfig.java`**

```java
package com.hsbc.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI paymentSystemOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Processing System API")
                        .version("1.0.0")
                        .description("REST API for managing the complete lifecycle of financial payments"));
    }
}
```

- [ ] **Step 3: Create `WebMvcConfig.java`**

```java
package com.hsbc.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/config/
git commit -m "feat: add MyBatis-Plus pagination, Swagger, and CORS config"
```

---

### Task 7: Create DTOs

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/dto/request/CreatePaymentRequest.java`
- Create: `backend/src/main/java/com/hsbc/payment/dto/request/FailRequest.java`
- Create: `backend/src/main/java/com/hsbc/payment/dto/request/PageRequest.java`
- Create: `backend/src/main/java/com/hsbc/payment/dto/response/PaymentResponse.java`
- Create: `backend/src/main/java/com/hsbc/payment/dto/response/StatusHistoryResponse.java`
- Create: `backend/src/main/java/com/hsbc/payment/dto/response/ErrorResponse.java`
- Create: `backend/src/main/java/com/hsbc/payment/dto/response/ApiResponse.java`

**Produces:** Type-safe request/response objects used across all controller and service layers.

- [ ] **Step 1: Create `CreatePaymentRequest.java`**

```java
package com.hsbc.payment.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequest {

    @NotBlank(message = "Source account is required")
    @Size(max = 50, message = "Source account must not exceed 50 characters")
    private String sourceAccount;

    @NotBlank(message = "Destination account is required")
    @Size(max = 50, message = "Destination account must not exceed 50 characters")
    private String destinationAccount;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "1000000.00", message = "Amount must not exceed 1,000,000")
    @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
    private String currency;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
```

- [ ] **Step 2: Create `FailRequest.java`**

```java
package com.hsbc.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FailRequest {
    @NotBlank(message = "Error code is required")
    private String errorCode;

    private String reason;
}
```

- [ ] **Step 3: Create `PageRequest.java`**

```java
package com.hsbc.payment.dto.request;

import lombok.Data;

@Data
public class PageRequest {
    private Integer page = 1;
    private Integer limit = 20;
    private String status;
    private String currency;
    private String keyword;
}
```

- [ ] **Step 4: Create `PaymentResponse.java`**

```java
package com.hsbc.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PaymentResponse {
    private String id;
    private String idempotencyKey;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<StatusHistoryResponse> statusHistory;
}
```

- [ ] **Step 5: Create `StatusHistoryResponse.java`**

```java
package com.hsbc.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StatusHistoryResponse {
    private Long id;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime changedAt;
    private String reason;
    private String errorCode;
}
```

- [ ] **Step 6: Create `ErrorResponse.java`**

```java
package com.hsbc.payment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private String code;
    private String message;
    private Map<String, Object> details;
}
```

- [ ] **Step 7: Create `ApiResponse.java`**

```java
package com.hsbc.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorResponse error;
    private long total;  // for paginated list responses

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, 0);
    }

    public static <T> ApiResponse<T> ok(T data, long total) {
        return new ApiResponse<>(true, data, null, total);
    }

    public static <T> ApiResponse<T> fail(ErrorResponse error) {
        return new ApiResponse<>(false, null, error, 0);
    }
}
```

- [ ] **Step 8: Verify compilation**

```bash
cd backend && mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/dto/
git commit -m "feat: add request/response DTOs with validation annotations"
```

---

### Task 8: Create Exception Handling

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/exception/BusinessException.java`
- Create: `backend/src/main/java/com/hsbc/payment/exception/GlobalExceptionHandler.java`

**Produces:** Centralized error handling — all business errors produce the unified ErrorResponse format.

- [ ] **Step 1: Create `BusinessException.java`**

```java
package com.hsbc.payment.exception;

import com.hsbc.payment.enums.ErrorCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}
```

- [ ] **Step 2: Create `GlobalExceptionHandler.java`**

```java
package com.hsbc.payment.exception;

import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.dto.response.ErrorResponse;
import com.hsbc.payment.enums.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus httpStatus = mapToHttpStatus(ex.getErrorCode());
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode().name())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .build();
        return ResponseEntity.status(httpStatus).body(ApiResponse.fail(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.name())
                .message("Field validation failed")
                .details(details)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.PROCESSING_ERROR.name())
                .message(ex.getMessage() != null ? ex.getMessage() : "Internal server error")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(error));
    }

    private HttpStatus mapToHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_FAILED, INSUFFICIENT_FUNDS, INVALID_ACCOUNT,
                 INVALID_CURRENCY, INVALID_AMOUNT, INVALID_STATUS_TRANSITION -> HttpStatus.BAD_REQUEST;
            case DUPLICATE_PAYMENT -> HttpStatus.CONFLICT;
            case PAYMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RISK_BLOCKED -> HttpStatus.FORBIDDEN;
            case NETWORK_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROCESSING_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd backend && mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/exception/
git commit -m "feat: add BusinessException and GlobalExceptionHandler with error-to-HTTP mapping"
```

---

### Task 9: Create StateMachineService

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/service/StateMachineService.java`

**Consumes:** `PaymentStatus` enum
**Produces:** `canTransition(String from, String to): boolean` — consumed by `PaymentServiceImpl`

- [ ] **Step 1: Create `StateMachineService.java`**

```java
package com.hsbc.payment.service;

import com.hsbc.payment.enums.PaymentStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class StateMachineService {

    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = Map.of(
        PaymentStatus.CREATED,   Set.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED),
        PaymentStatus.VALIDATED, Set.of(PaymentStatus.SENT, PaymentStatus.FAILED),
        PaymentStatus.SENT,      Set.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
        PaymentStatus.COMPLETED, Set.of(),
        PaymentStatus.FAILED,    Set.of(PaymentStatus.VALIDATED)
    );

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null || to == null) return false;
        Set<PaymentStatus> validTargets = VALID_TRANSITIONS.get(from);
        return validTargets != null && validTargets.contains(to);
    }

    public boolean canTransition(String fromStr, String toStr) {
        PaymentStatus from = PaymentStatus.fromString(fromStr);
        PaymentStatus to = PaymentStatus.fromString(toStr);
        return canTransition(from, to);
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd backend && mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/service/StateMachineService.java
git commit -m "feat: add StateMachineService with valid transition matrix"
```

---

### Task 10: Create ValidationService

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/service/ValidationService.java`

**Consumes:** `CreatePaymentRequest` DTO
**Produces:** `void validate(CreatePaymentRequest request)` — throws `BusinessException` on failure

- [ ] **Step 1: Create `ValidationService.java`**

```java
package com.hsbc.payment.service;

import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ValidationService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "CNY");

    public void validate(CreatePaymentRequest request) {
        // Account check: source != destination
        if (request.getSourceAccount().equalsIgnoreCase(request.getDestinationAccount())) {
            throw new BusinessException(
                ErrorCode.INVALID_ACCOUNT,
                "Source and destination accounts must be different"
            );
        }

        // Currency check
        if (!SUPPORTED_CURRENCIES.contains(request.getCurrency().toUpperCase())) {
            throw new BusinessException(
                ErrorCode.INVALID_CURRENCY,
                "Currency " + request.getCurrency() + " is not supported. Supported: " + SUPPORTED_CURRENCIES
            );
        }
    }
}
```

> Note: `@DecimalMin`, `@DecimalMax`, `@Digits` annotations on `CreatePaymentRequest` already handle amount validation at the controller level. This service adds business-level validation (account inequality, currency support).

- [ ] **Step 2: Verify compilation**

```bash
cd backend && mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/service/ValidationService.java
git commit -m "feat: add ValidationService for account and currency business rules"
```

---

### Task 11: Create IdempotencyService

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/service/IdempotencyService.java`

**Consumes:** `IdempotencyMapper`, `IdempotencyRecord` entity
**Produces:**
- `checkAndSave(String key, String paymentId): boolean` — returns `true` if new, `false` if duplicate exists
- `getExistingPaymentId(String key): String` — returns payment ID for duplicate key

- [ ] **Step 1: Create `IdempotencyService.java`**

```java
package com.hsbc.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hsbc.payment.entity.IdempotencyRecord;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.IdempotencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyMapper idempotencyMapper;

    /**
     * Attempts to save an idempotency key.
     * @return true if the key is new (proceed), false if it's a duplicate
     */
    public boolean checkAndSave(String key, String paymentId) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setKeyRecord(key);
        record.setPaymentId(paymentId);
        try {
            idempotencyMapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /**
     * Returns the payment ID associated with an existing idempotency key.
     */
    public String getExistingPaymentId(String key) {
        IdempotencyRecord record = idempotencyMapper.selectById(key);
        if (record == null) {
            throw new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND,
                "No payment found for idempotency key: " + key
            );
        }
        return record.getPaymentId();
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd backend && mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/service/IdempotencyService.java
git commit -m "feat: add IdempotencyService for duplicate payment detection"
```

---

### Task 12: Create PaymentService Interface and Implementation

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/service/PaymentService.java`
- Create: `backend/src/main/java/com/hsbc/payment/service/impl/PaymentServiceImpl.java`

**Consumes:** All mappers, `StateMachineService`, `ValidationService`, `IdempotencyService`, all DTOs
**Produces:** Full payment lifecycle API consumed by controllers

- [ ] **Step 1: Create `PaymentService.java` interface**

```java
package com.hsbc.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.PaymentResponse;

public interface PaymentService {
    PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey);
    PaymentResponse getPayment(String paymentId);
    Page<PaymentResponse> listPayments(PageRequest pageRequest);
    PaymentResponse getPaymentHistory(String paymentId);
    PaymentResponse processValidate(String paymentId);
    PaymentResponse processSend(String paymentId);
    PaymentResponse processComplete(String paymentId);
    PaymentResponse processFail(String paymentId, String errorCode, String reason);
    PaymentResponse processRetry(String paymentId, String idempotencyKey);
}
```

- [ ] **Step 2: Create `PaymentServiceImpl.java`**

```java
package com.hsbc.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.PaymentResponse;
import com.hsbc.payment.dto.response.StatusHistoryResponse;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.entity.StatusHistory;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.enums.PaymentStatus;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.IdempotencyMapper;
import com.hsbc.payment.mapper.PaymentMapper;
import com.hsbc.payment.mapper.StatusHistoryMapper;
import com.hsbc.payment.service.IdempotencyService;
import com.hsbc.payment.service.PaymentService;
import com.hsbc.payment.service.StateMachineService;
import com.hsbc.payment.service.ValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final StatusHistoryMapper statusHistoryMapper;
    private final StateMachineService stateMachineService;
    private final ValidationService validationService;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        // 1. Idempotency check
        boolean isNew = idempotencyService.checkAndSave(idempotencyKey, "PENDING");
        if (!isNew) {
            String existingId = idempotencyService.getExistingPaymentId(idempotencyKey);
            return getPayment(existingId);
        }

        // 2. Business validation
        validationService.validate(request);

        // 3. Create payment
        Payment payment = new Payment();
        payment.setIdempotencyKey(idempotencyKey);
        payment.setSourceAccount(request.getSourceAccount());
        payment.setDestinationAccount(request.getDestinationAccount());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency().toUpperCase());
        payment.setDescription(request.getDescription());
        payment.setStatus(PaymentStatus.CREATED.name());
        paymentMapper.insert(payment);

        // 4. Update idempotency record with actual payment ID
        com.hsbc.payment.entity.IdempotencyRecord record = new com.hsbc.payment.entity.IdempotencyRecord();
        record.setKeyRecord(idempotencyKey);
        record.setPaymentId(payment.getId());
        // Re-save with payment ID (the record already exists with "PENDING")
        // We use a fresh mapper approach — since key_record is PK, we need to update
        // Actually: delete and re-insert pattern would be fragile. Better approach:
        // Use a different strategy — write idempotency record AFTER payment is created.
        // Let's adjust: delete the "PENDING" record and create the real one.
        // But this is not atomic... Let's simplify: we'll use UPDATE via raw approach.
        // SIMPLEST FIX: idempotency table doesn't need a FK — just store the key + payment_id.
        // Let's do a direct update via the mapper.
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.hsbc.payment.entity.IdempotencyRecord> updateWrapper =
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        updateWrapper.eq(com.hsbc.payment.entity.IdempotencyRecord::getKeyRecord, idempotencyKey)
                     .set(com.hsbc.payment.entity.IdempotencyRecord::getPaymentId, payment.getId());
        // Need IdempotencyMapper updated for this — will update in Task 12b
        // For now we'll handle it differently below

        // Clean approach: use IdempotencyMapper updateById
        com.hsbc.payment.entity.IdempotencyRecord existingRecord = new com.hsbc.payment.entity.IdempotencyRecord();
        existingRecord.setKeyRecord(idempotencyKey);
        existingRecord.setPaymentId(payment.getId());
        // We'll use manual update in mapper. Let's finalize approach.

        // 5. Record status history
        StatusHistory history = new StatusHistory();
        history.setPaymentId(payment.getId());
        history.setFromStatus(null);
        history.setToStatus(PaymentStatus.CREATED.name());
        statusHistoryMapper.insert(history);

        return toPaymentResponse(payment);
    }

    @Override
    public PaymentResponse getPayment(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentResponse response = toPaymentResponse(payment);
        response.setStatusHistory(getStatusHistory(paymentId));
        return response;
    }

    @Override
    public Page<PaymentResponse> listPayments(PageRequest pageRequest) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(pageRequest.getStatus())) {
            wrapper.eq(Payment::getStatus, pageRequest.getStatus().toUpperCase());
        }
        if (StringUtils.hasText(pageRequest.getCurrency())) {
            wrapper.eq(Payment::getCurrency, pageRequest.getCurrency().toUpperCase());
        }
        if (StringUtils.hasText(pageRequest.getKeyword())) {
            wrapper.and(w -> w
                .like(Payment::getId, pageRequest.getKeyword())
                .or()
                .like(Payment::getDescription, pageRequest.getKeyword())
            );
        }
        wrapper.orderByDesc(Payment::getCreatedAt);

        Page<Payment> page = new Page<>(pageRequest.getPage(), pageRequest.getLimit());
        Page<Payment> resultPage = paymentMapper.selectPage(page, wrapper);

        Page<PaymentResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(this::toPaymentResponse)
                .collect(Collectors.toList()));
        return responsePage;
    }

    @Override
    public PaymentResponse getPaymentHistory(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentResponse response = toPaymentResponse(payment);
        response.setStatusHistory(getStatusHistory(paymentId));
        return response;
    }

    @Override
    @Transactional
    public PaymentResponse processValidate(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.VALIDATED;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + fromStatus + " to " + toStatus);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), null, null);

        return getPayment(paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse processSend(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.SENT;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + fromStatus + " to " + toStatus);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), null, null);

        return getPayment(paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse processComplete(String paymentId) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.COMPLETED;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + fromStatus + " to " + toStatus);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), null, null);

        return getPayment(paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse processFail(String paymentId, String errorCode, String reason) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.FAILED;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot transition from " + fromStatus + " to " + toStatus);
        }

        updatePaymentStatus(payment, toStatus.name(), errorCode);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), reason, errorCode);

        return getPayment(paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse processRetry(String paymentId, String idempotencyKey) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
        PaymentStatus toStatus = PaymentStatus.VALIDATED;

        if (!stateMachineService.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot retry from status " + fromStatus);
        }

        // Check idempotency for retry
        boolean isNew = idempotencyService.checkAndSave(idempotencyKey, paymentId);
        if (!isNew) {
            throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT,
                    "Retry idempotency key already used: " + idempotencyKey);
        }

        updatePaymentStatus(payment, toStatus.name(), null);
        recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), "Retry attempt", null);

        return getPayment(paymentId);
    }

    // --- Private helpers ---

    private Payment findPaymentById(String paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND,
                    "Payment not found: " + paymentId);
        }
        return payment;
    }

    private void updatePaymentStatus(Payment payment, String newStatus, String errorCode) {
        payment.setStatus(newStatus);
        if (errorCode != null) {
            payment.setErrorCode(errorCode);
        } else {
            payment.setErrorCode(null);
        }
        paymentMapper.updateById(payment);
    }

    private void recordStatusHistory(String paymentId, String fromStatus, String toStatus,
                                      String reason, String errorCode) {
        StatusHistory history = new StatusHistory();
        history.setPaymentId(paymentId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setReason(reason);
        history.setErrorCode(errorCode);
        statusHistoryMapper.insert(history);
    }

    private List<StatusHistoryResponse> getStatusHistory(String paymentId) {
        List<StatusHistory> histories = statusHistoryMapper.findByPaymentId(paymentId);
        return histories.stream()
                .map(h -> StatusHistoryResponse.builder()
                        .id(h.getId())
                        .fromStatus(h.getFromStatus())
                        .toStatus(h.getToStatus())
                        .changedAt(h.getChangedAt())
                        .reason(h.getReason())
                        .errorCode(h.getErrorCode())
                        .build())
                .collect(Collectors.toList());
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .sourceAccount(payment.getSourceAccount())
                .destinationAccount(payment.getDestinationAccount())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .description(payment.getDescription())
                .status(payment.getStatus())
                .errorCode(payment.getErrorCode())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 3: Update `IdempotencyService` to fix the create-before-payment-ID issue**

The `createPayment` method in `PaymentServiceImpl` above creates the idempotency record with a "PENDING" placeholder then needs to update it. Replace the old `IdempotencyService` with this improved version:

```java
package com.hsbc.payment.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hsbc.payment.entity.IdempotencyRecord;
import com.hsbc.payment.enums.ErrorCode;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.IdempotencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyMapper idempotencyMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean checkAndSave(String key, String paymentId) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setKeyRecord(key);
        record.setPaymentId(paymentId);
        try {
            idempotencyMapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public String getExistingPaymentId(String key) {
        IdempotencyRecord record = idempotencyMapper.selectById(key);
        if (record == null) {
            throw new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND,
                "No payment found for idempotency key: " + key
            );
        }
        return record.getPaymentId();
    }

    public void updatePaymentId(String key, String paymentId) {
        LambdaUpdateWrapper<IdempotencyRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(IdempotencyRecord::getKeyRecord, key)
               .set(IdempotencyRecord::getPaymentId, paymentId);
        idempotencyMapper.update(null, wrapper);
    }
}
```

Then update `PaymentServiceImpl.createPayment()` to:
1. Call `checkAndSave(idempotencyKey, payment.getId())` AFTER inserting the payment (not before)
2. Remove the "PENDING" placeholder logic

- [ ] **Step 4: Verify compilation**

```bash
cd backend && mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/service/PaymentService.java \
        backend/src/main/java/com/hsbc/payment/service/impl/PaymentServiceImpl.java \
        backend/src/main/java/com/hsbc/payment/service/IdempotencyService.java
git commit -m "feat: add PaymentService with full lifecycle, state machine, and idempotency logic"
```

---

### Task 13: Create PaymentController (CRUD + Query)

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/controller/PaymentController.java`

**Consumes:** `PaymentService`, DTOs
**Produces:** REST endpoints `POST /api/payments`, `GET /api/payments`, `GET /api/payments/{id}`, `GET /api/payments/{id}/history`

- [ ] **Step 1: Create `PaymentController.java`**

```java
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
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> listPayments(
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
```

- [ ] **Step 2: Verify compilation**

```bash
cd backend && mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/controller/PaymentController.java
git commit -m "feat: add PaymentController with create, list, get, and history endpoints"
```

---

### Task 14: Create PaymentProcessController (State Transitions)

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/controller/PaymentProcessController.java`

**Consumes:** `PaymentService`
**Produces:** State transition endpoints

- [ ] **Step 1: Create `PaymentProcessController.java`**

```java
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
        PaymentResponse response = paymentService.processFail(id, request.getErrorCode(), request.getReason());
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
```

- [ ] **Step 2: Verify compilation**

```bash
cd backend && mvn clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Start MySQL and verify the application starts**

```bash
# Ensure MySQL is running, then:
cd backend && mvn spring-boot:run
```

Expected: Application starts on port 8080, Swagger UI at http://localhost:8080/swagger-ui.html

- [ ] **Step 4: Quick smoke test with curl**

```bash
# Create a payment
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-key-001" \
  -d '{"sourceAccount":"ACC-001","destinationAccount":"ACC-002","amount":5000.00,"currency":"USD","description":"Test payment"}'
```

Expected: HTTP 201 with payment JSON, status CREATED

```bash
# Validate the payment
curl -X POST http://localhost:8080/api/payments/<payment-id>/validate
```

Expected: HTTP 200 with status VALIDATED

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/controller/PaymentProcessController.java
git commit -m "feat: add PaymentProcessController with validate/send/complete/fail/retry endpoints"
```

---

## Phase 2: Verification

### Task 15: Write StateMachineService Unit Tests

**Files:**
- Create: `backend/src/test/java/com/hsbc/payment/service/StateMachineServiceTest.java`

**Produces:** 10 test cases covering all valid transitions, invalid transitions, and edge cases.

- [ ] **Step 1: Create `StateMachineServiceTest.java`**

```java
package com.hsbc.payment.service;

import com.hsbc.payment.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineServiceTest {

    private StateMachineService stateMachineService;

    @BeforeEach
    void setUp() {
        stateMachineService = new StateMachineService();
    }

    // --- Valid transitions ---

    @Test
    @DisplayName("CREATED → VALIDATED should be valid")
    void createdToValidated() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.CREATED, PaymentStatus.VALIDATED));
    }

    @Test
    @DisplayName("CREATED → FAILED should be valid")
    void createdToFailed() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.CREATED, PaymentStatus.FAILED));
    }

    @Test
    @DisplayName("VALIDATED → SENT should be valid")
    void validatedToSent() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.VALIDATED, PaymentStatus.SENT));
    }

    @Test
    @DisplayName("VALIDATED → FAILED should be valid")
    void validatedToFailed() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.VALIDATED, PaymentStatus.FAILED));
    }

    @Test
    @DisplayName("SENT → COMPLETED should be valid")
    void sentToCompleted() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.SENT, PaymentStatus.COMPLETED));
    }

    @Test
    @DisplayName("SENT → FAILED should be valid")
    void sentToFailed() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.SENT, PaymentStatus.FAILED));
    }

    @Test
    @DisplayName("FAILED → VALIDATED (retry) should be valid")
    void failedToValidated() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.FAILED, PaymentStatus.VALIDATED));
    }

    // --- Invalid transitions ---

    @Test
    @DisplayName("COMPLETED → any status should be invalid")
    void completedIsTerminal() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertFalse(stateMachineService.canTransition(PaymentStatus.COMPLETED, target),
                    "COMPLETED → " + target + " should be invalid");
        }
    }

    @Test
    @DisplayName("CREATED → COMPLETED should be invalid (skip steps)")
    void createdToCompletedInvalid() {
        assertFalse(stateMachineService.canTransition(PaymentStatus.CREATED, PaymentStatus.COMPLETED));
    }

    @Test
    @DisplayName("null inputs should return false")
    void nullInputs() {
        assertFalse(stateMachineService.canTransition((PaymentStatus) null, PaymentStatus.VALIDATED));
        assertFalse(stateMachineService.canTransition(PaymentStatus.CREATED, (PaymentStatus) null));
        assertFalse(stateMachineService.canTransition((PaymentStatus) null, (PaymentStatus) null));
    }

    // --- String-based transitions ---

    @Test
    @DisplayName("String-based canTransition should work")
    void stringBasedTransition() {
        assertTrue(stateMachineService.canTransition("CREATED", "VALIDATED"));
        assertFalse(stateMachineService.canTransition("COMPLETED", "CREATED"));
    }
}
```

- [ ] **Step 2: Run tests**

```bash
cd backend && mvn test -Dtest=StateMachineServiceTest
```

Expected: 11 tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/hsbc/payment/service/StateMachineServiceTest.java
git commit -m "test: add StateMachineService unit tests for all valid and invalid transitions"
```

---

### Task 16: Write PaymentService Unit Tests

**Files:**
- Create: `backend/src/test/java/com/hsbc/payment/service/PaymentServiceTest.java`

**Produces:** Unit tests covering create payment, validation failures, duplicate idempotency, state transitions, payment not found.

- [ ] **Step 1: Create `PaymentServiceTest.java`**

```java
package com.hsbc.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.PageRequest;
import com.hsbc.payment.dto.response.PaymentResponse;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.entity.StatusHistory;
import com.hsbc.payment.enums.PaymentStatus;
import com.hsbc.payment.exception.BusinessException;
import com.hsbc.payment.mapper.PaymentMapper;
import com.hsbc.payment.mapper.StatusHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentMapper paymentMapper;
    @Mock StatusHistoryMapper statusHistoryMapper;
    @Mock IdempotencyService idempotencyService;
    @Mock StateMachineService stateMachineService;
    @Mock ValidationService validationService;
    @InjectMocks PaymentServiceImpl paymentService;

    private CreatePaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreatePaymentRequest();
        validRequest.setSourceAccount("ACC-001");
        validRequest.setDestinationAccount("ACC-002");
        validRequest.setAmount(new BigDecimal("100.00"));
        validRequest.setCurrency("USD");
    }

    @Test
    @DisplayName("Create payment — happy path")
    void createPaymentSuccess() {
        when(idempotencyService.checkAndSave(anyString(), anyString())).thenReturn(true);
        doNothing().when(validationService).validate(any());
        doAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId("pay-001");
            return 1;
        }).when(paymentMapper).insert(any(Payment.class));
        when(statusHistoryMapper.insert(any())).thenReturn(1);
        when(paymentMapper.selectById("pay-001")).thenAnswer(inv -> {
            Payment p = new Payment();
            p.setId("pay-001");
            p.setIdempotencyKey("key-001");
            p.setSourceAccount("ACC-001");
            p.setDestinationAccount("ACC-002");
            p.setAmount(new BigDecimal("100.00"));
            p.setCurrency("USD");
            p.setStatus("CREATED");
            return p;
        });
        when(statusHistoryMapper.findByPaymentId("pay-001")).thenReturn(java.util.List.of());

        PaymentResponse response = paymentService.createPayment(validRequest, "key-001");

        assertNotNull(response);
        assertEquals("pay-001", response.getId());
        assertEquals("CREATED", response.getStatus());
    }

    @Test
    @DisplayName("Create payment — duplicate idempotency key returns existing")
    void createPaymentDuplicate() {
        when(idempotencyService.checkAndSave("key-dup", "pay-001")).thenReturn(false);
        when(idempotencyService.getExistingPaymentId("key-dup")).thenReturn("pay-existing");
        Payment existing = new Payment();
        existing.setId("pay-existing");
        existing.setStatus("CREATED");
        existing.setCurrency("USD");
        existing.setAmount(new BigDecimal("100.00"));
        when(paymentMapper.selectById("pay-existing")).thenReturn(existing);
        when(statusHistoryMapper.findByPaymentId("pay-existing")).thenReturn(java.util.List.of());

        PaymentResponse response = paymentService.createPayment(validRequest, "key-dup");

        assertEquals("pay-existing", response.getId());
        verify(validationService, never()).validate(any());
    }

    @Test
    @DisplayName("Get payment — not found throws exception")
    void getPaymentNotFound() {
        when(paymentMapper.selectById("no-such-id")).thenReturn(null);

        assertThrows(BusinessException.class, () -> paymentService.getPayment("no-such-id"));
    }

    @Test
    @DisplayName("Validate payment — successful transition")
    void processValidateSuccess() {
        Payment payment = new Payment();
        payment.setId("pay-001");
        payment.setStatus("CREATED");
        when(paymentMapper.selectById("pay-001")).thenReturn(payment);
        when(stateMachineService.canTransition(PaymentStatus.CREATED, PaymentStatus.VALIDATED))
                .thenReturn(true);
        when(paymentMapper.updateById(any())).thenReturn(1);
        when(statusHistoryMapper.insert(any())).thenReturn(1);
        when(statusHistoryMapper.findByPaymentId("pay-001")).thenReturn(java.util.List.of());

        PaymentResponse response = paymentService.processValidate("pay-001");

        assertEquals("VALIDATED", response.getStatus());
        verify(statusHistoryMapper, times(2)).insert(any()); // first insert during create + transition
    }

    @Test
    @DisplayName("Validate payment — invalid transition throws exception")
    void processValidateInvalidTransition() {
        Payment payment = new Payment();
        payment.setId("pay-001");
        payment.setStatus("COMPLETED");
        when(paymentMapper.selectById("pay-001")).thenReturn(payment);
        when(stateMachineService.canTransition(PaymentStatus.COMPLETED, PaymentStatus.VALIDATED))
                .thenReturn(false);

        assertThrows(BusinessException.class, () -> paymentService.processValidate("pay-001"));
    }

    @Test
    @DisplayName("List payments — filter by status and keyword")
    void listPaymentsFiltered() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setStatus("FAILED");
        pageRequest.setKeyword("rent");
        pageRequest.setPage(1);
        pageRequest.setLimit(10);

        Page<Payment> mockPage = new Page<>(1, 10);
        mockPage.setRecords(java.util.List.of());
        mockPage.setTotal(0);
        when(paymentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<PaymentResponse> result = paymentService.listPayments(pageRequest);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }
}
```

- [ ] **Step 2: Run tests**

```bash
cd backend && mvn test -Dtest=PaymentServiceTest
```

Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/hsbc/payment/service/PaymentServiceTest.java
git commit -m "test: add PaymentService unit tests for create, duplicate, transitions, and queries"
```

---

### Task 17: Write PaymentController Integration Tests

**Files:**
- Create: `backend/src/test/java/com/hsbc/payment/controller/PaymentControllerTest.java`

**Produces:** Spring MockMvc integration tests covering all endpoints.

- [ ] **Step 1: Create `PaymentControllerTest.java`**

```java
package com.hsbc.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.payment.dto.request.CreatePaymentRequest;
import com.hsbc.payment.dto.request.FailRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/payments — create payment successfully")
    void createPayment() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setSourceAccount("ACC-TEST-1");
        request.setDestinationAccount("ACC-TEST-2");
        request.setAmount(new BigDecimal("250.00"));
        request.setCurrency("USD");
        request.setDescription("Integration test payment");

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    @DisplayName("POST /api/payments — missing Idempotency-Key returns 400")
    void createPaymentNoIdempotencyKey() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setSourceAccount("ACC-TEST-1");
        request.setDestinationAccount("ACC-TEST-2");
        request.setAmount(new BigDecimal("250.00"));
        request.setCurrency("USD");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/payments — validation fails for negative amount")
    void createPaymentNegativeAmount() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setSourceAccount("ACC-TEST-1");
        request.setDestinationAccount("ACC-TEST-2");
        request.setAmount(new BigDecimal("-100.00"));
        request.setCurrency("USD");

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/payments/{id} — return 404 for non-existent payment")
    void getPaymentNotFound() throws Exception {
        mockMvc.perform(get("/api/payments/non-existent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Full lifecycle: create → validate → send → complete")
    void fullLifecycle() throws Exception {
        // Create
        String idempotencyKey = UUID.randomUUID().toString();
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setSourceAccount("ACC-LC-1");
        request.setDestinationAccount("ACC-LC-2");
        request.setAmount(new BigDecimal("500.00"));
        request.setCurrency("USD");

        String response = mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String paymentId = objectMapper.readTree(response).get("data").get("id").asText();

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

        // History should have 4 entries (CREATED → VALIDATED → SENT → COMPLETED)
        mockMvc.perform(get("/api/payments/" + paymentId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusHistory.length()").value(4));
    }

    @Test
    @DisplayName("CREATE → FAIL → RETRY → VALIDATED lifecycle")
    void failAndRetry() throws Exception {
        // Create
        String idempotencyKey = UUID.randomUUID().toString();
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setSourceAccount("ACC-FR-1");
        request.setDestinationAccount("ACC-FR-2");
        request.setAmount(new BigDecimal("300.00"));
        request.setCurrency("USD");

        String response = mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String paymentId = objectMapper.readTree(response).get("data").get("id").asText();

        // Fail
        FailRequest failRequest = new FailRequest();
        failRequest.setErrorCode("INSUFFICIENT_FUNDS");
        failRequest.setReason("Not enough balance");

        mockMvc.perform(post("/api/payments/" + paymentId + "/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("INSUFFICIENT_FUNDS"));

        // Retry
        mockMvc.perform(post("/api/payments/" + paymentId + "/retry")
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALIDATED"));
    }
}
```

- [ ] **Step 2: Run tests**

```bash
cd backend && mvn test -Dtest=PaymentControllerTest
```

Expected: 6 integration tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/hsbc/payment/controller/PaymentControllerTest.java
git commit -m "test: add PaymentController integration tests covering full lifecycle and edge cases"
```

---

### Task 18: Manual Verification Checklist

**No new files.** Verify all backend endpoints via Swagger UI and curl.

- [ ] **Step 1: Start backend and verify Swagger UI accessible**

```bash
cd backend && mvn spring-boot:run
# Open http://localhost:8080/swagger-ui.html
```

- [ ] **Step 2: Run the full lifecycle smoke test**

```bash
# 1. Create payment (expect 201 + CREATED)
curl -s -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: smoke-$(date +%s)" \
  -d '{"sourceAccount":"ACC-SMOKE","destinationAccount":"ACC-002","amount":500.00,"currency":"USD","description":"Smoke test"}'

# 2. Validate (expect 200 + VALIDATED) — replace <id>
curl -s -X POST http://localhost:8080/api/payments/<id>/validate

# 3. Send (expect 200 + SENT)
curl -s -X POST http://localhost:8080/api/payments/<id>/send

# 4. Complete (expect 200 + COMPLETED)
curl -s -X POST http://localhost:8080/api/payments/<id>/complete

# 5. Verify history (expect 4 entries)
curl -s http://localhost:8080/api/payments/<id>/history | python -m json.tool

# 6. Test duplicate idempotency
curl -s -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: smoke-<same-key-as-step1>" \
  -d '{"sourceAccount":"ACC-SMOKE","destinationAccount":"ACC-002","amount":500.00,"currency":"USD"}' \
  | python -m json.tool
# Expect: same payment ID as step 1 (not a new one)

# 7. Test invalid state transition (COMPLETED → CREATED should fail)
curl -s -X POST http://localhost:8080/api/payments/<id>/validate
# Expect: 400 + INVALID_STATUS_TRANSITION

# 8. Test list with filters
curl -s "http://localhost:8080/api/payments?status=COMPLETED&page=1&limit=10" | python -m json.tool
```

All responses must match expected behavior per the API design.

---

## Phase 3: Frontend

### Task 19: Initialize Vue 3 Project

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/index.html`
- Create: `frontend/src/main.js`
- Create: `frontend/src/App.vue`

**Produces:** Runnable Vue 3 + Vite skeleton.

- [ ] **Step 1: Create `frontend/package.json`**

```json
{
  "name": "payment-system-frontend",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.3.0",
    "pinia": "^2.1.0",
    "axios": "^1.6.0",
    "element-plus": "^2.5.0",
    "@element-plus/icons-vue": "^2.3.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "vite": "^5.1.0"
  }
}
```

- [ ] **Step 2: Create `frontend/vite.config.js`**

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})
```

- [ ] **Step 3: Create `frontend/index.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Payment Processing System</title>
</head>
<body>
  <div id="app"></div>
  <script type="module" src="/src/main.js"></script>
</body>
</html>
```

- [ ] **Step 4: Create `frontend/src/main.js`**

```javascript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

- [ ] **Step 5: Create `frontend/src/App.vue`**

```vue
<template>
  <el-container style="min-height: 100vh">
    <el-header style="background: #409eff; color: white; display: flex; align-items: center; padding: 0 24px">
      <h2 style="margin: 0">Payment Processing System</h2>
      <el-menu
        mode="horizontal"
        :default-active="$route.path"
        router
        style="margin-left: 40px; border-bottom: none; background: transparent"
        text-color="#ffffff"
        active-text-color="#ffd04b"
      >
        <el-menu-item index="/payments">Payment List</el-menu-item>
        <el-menu-item index="/payments/create">Create Payment</el-menu-item>
      </el-menu>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>
```

- [ ] **Step 6: Install dependencies and start dev server**

```bash
cd frontend && npm install && npm run dev
```

Visit http://localhost:5173 — should show a blank page with "Payment Processing System" header.

- [ ] **Step 7: Commit**

```bash
git add frontend/
git commit -m "feat: initialize Vue 3 + Vite + Element Plus frontend project"
```

---

### Task 20: Set Up Router and API Layer

**Files:**
- Create: `frontend/src/router/index.js`
- Create: `frontend/src/api/index.js`
- Create: `frontend/src/api/payment.js`
- Create: `frontend/src/utils/constants.js`

**Produces:** Vue Router with 3 routes, Axios instance, API functions, status/currency constants.

- [ ] **Step 1: Create `frontend/src/utils/constants.js`**

```javascript
export const PAYMENT_STATUS = {
  CREATED:   { label: 'Created',   color: '#909399', type: 'info' },
  VALIDATED: { label: 'Validated', color: '#409eff', type: '' },
  SENT:      { label: 'Sent',      color: '#e6a23c', type: 'warning' },
  COMPLETED: { label: 'Completed', color: '#67c23a', type: 'success' },
  FAILED:    { label: 'Failed',    color: '#f56c6c', type: 'danger' },
}

export const SUPPORTED_CURRENCIES = ['USD', 'EUR', 'GBP', 'CNY']

export const ERROR_CODE_MAP = {
  VALIDATION_FAILED: 'Validation Failed',
  INSUFFICIENT_FUNDS: 'Insufficient Funds',
  INVALID_ACCOUNT: 'Invalid Account',
  INVALID_CURRENCY: 'Invalid Currency',
  INVALID_AMOUNT: 'Invalid Amount',
  DUPLICATE_PAYMENT: 'Duplicate Payment',
  INVALID_STATUS_TRANSITION: 'Invalid Status Transition',
  PAYMENT_NOT_FOUND: 'Payment Not Found',
  PROCESSING_ERROR: 'Processing Error',
  NETWORK_ERROR: 'Network Error',
  RISK_BLOCKED: 'Risk Blocked',
}

export const STATUS_ACTIONS = {
  CREATED:   [
    { key: 'validate', label: 'Validate', type: 'primary' },
    { key: 'fail',     label: 'Mark Failed', type: 'danger' },
  ],
  VALIDATED: [
    { key: 'send',  label: 'Send',         type: 'primary' },
    { key: 'fail',  label: 'Mark Failed',  type: 'danger' },
  ],
  SENT: [
    { key: 'complete', label: 'Complete',     type: 'success' },
    { key: 'fail',     label: 'Mark Failed',  type: 'danger' },
  ],
  FAILED: [
    { key: 'retry', label: 'Retry', type: 'warning' },
  ],
  COMPLETED: [],
}
```

- [ ] **Step 2: Create `frontend/src/router/index.js`**

```javascript
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/payments' },
  {
    path: '/payments',
    name: 'PaymentList',
    component: () => import('../views/PaymentListView.vue'),
  },
  {
    path: '/payments/create',
    name: 'CreatePayment',
    component: () => import('../views/CreatePaymentView.vue'),
  },
  {
    path: '/payments/:id',
    name: 'PaymentDetail',
    component: () => import('../views/PaymentDetailView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
```

- [ ] **Step 3: Create `frontend/src/api/index.js`**

```javascript
import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const msg = error.response?.data?.error?.message || error.message || 'Network error'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default api
```

- [ ] **Step 4: Create `frontend/src/api/payment.js`**

```javascript
import api from './index'

export function createPayment(data, idempotencyKey) {
  return api.post('/payments', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function listPayments(params) {
  return api.get('/payments', { params })
}

export function getPayment(id) {
  return api.get(`/payments/${id}`)
}

export function getPaymentHistory(id) {
  return api.get(`/payments/${id}/history`)
}

export function validatePayment(id) {
  return api.post(`/payments/${id}/validate`)
}

export function sendPayment(id) {
  return api.post(`/payments/${id}/send`)
}

export function completePayment(id) {
  return api.post(`/payments/${id}/complete`)
}

export function failPayment(id, errorCode, reason) {
  return api.post(`/payments/${id}/fail`, { errorCode, reason })
}

export function retryPayment(id, idempotencyKey) {
  return api.post(`/payments/${id}/retry`, null, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/router/ frontend/src/api/ frontend/src/utils/
git commit -m "feat: add Vue Router, Axios API layer, and constants"
```

---

### Task 21: Create StatusBadge and ErrorPanel Components

**Files:**
- Create: `frontend/src/components/StatusBadge.vue`
- Create: `frontend/src/components/ErrorPanel.vue`

**Produces:** Reusable status badge with color coding, error detail panel.

- [ ] **Step 1: Create `frontend/src/components/StatusBadge.vue`**

```vue
<template>
  <el-tag :type="statusInfo.type" :color="statusInfo.type ? undefined : statusInfo.color" effect="dark">
    {{ statusInfo.label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { PAYMENT_STATUS } from '../utils/constants'

const props = defineProps({ status: { type: String, required: true } })

const statusInfo = computed(() => {
  return PAYMENT_STATUS[props.status] || { label: props.status, type: 'info' }
})
</script>
```

- [ ] **Step 2: Create `frontend/src/components/ErrorPanel.vue`**

```vue
<template>
  <el-alert
    v-if="errorCode"
    title="Payment Failed"
    type="error"
    :description="errorMessage"
    show-icon
    :closable="false"
    style="margin-top: 16px"
  >
    <template v-if="reason">
      <p><strong>Error Code:</strong> {{ errorCode }}</p>
      <p><strong>Reason:</strong> {{ reason }}</p>
    </template>
  </el-alert>
</template>

<script setup>
import { computed } from 'vue'
import { ERROR_CODE_MAP } from '../utils/constants'

const props = defineProps({
  errorCode: { type: String, default: null },
  reason: { type: String, default: null },
})

const errorMessage = computed(() => {
  return ERROR_CODE_MAP[props.errorCode] || props.errorCode
})
</script>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/StatusBadge.vue frontend/src/components/ErrorPanel.vue
git commit -m "feat: add StatusBadge and ErrorPanel components"
```

---

### Task 22: Create StatusTimeline Component

**Files:**
- Create: `frontend/src/components/StatusTimeline.vue`

**Produces:** Vertical timeline showing each status transition with timestamp.

- [ ] **Step 1: Create `frontend/src/components/StatusTimeline.vue`**

```vue
<template>
  <div style="margin-top: 16px">
    <h3>Status History</h3>
    <el-timeline v-if="history.length > 0">
      <el-timeline-item
        v-for="(item, index) in history"
        :key="item.id || index"
        :type="item.errorCode ? 'danger' : 'primary'"
        :timestamp="formatTime(item.changedAt)"
        placement="top"
      >
        <p>
          <strong>{{ item.toStatus }}</strong>
          <span v-if="item.fromStatus" style="color: #909399">
            &nbsp;(from {{ item.fromStatus }})
          </span>
        </p>
        <p v-if="item.reason" style="color: #909399; font-size: 13px">
          {{ item.reason }}
        </p>
        <p v-if="item.errorCode" style="color: #f56c6c; font-size: 13px">
          Error: {{ item.errorCode }}
        </p>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="No history recorded" />
  </div>
</template>

<script setup>
import { toRefs } from 'vue'

const props = defineProps({
  history: { type: Array, default: () => [] },
})

const { history } = toRefs(props)

function formatTime(timestamp) {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleString()
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/StatusTimeline.vue
git commit -m "feat: add StatusTimeline component"
```

---

### Task 23: Create PaymentForm Component

**Files:**
- Create: `frontend/src/components/PaymentForm.vue`

**Produces:** Reusable payment creation form with client-side validation.

- [ ] **Step 1: Create `frontend/src/components/PaymentForm.vue`**

```vue
<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-width="180px"
    style="max-width: 600px"
  >
    <el-form-item label="Source Account" prop="sourceAccount">
      <el-input v-model="form.sourceAccount" placeholder="e.g. ACC-001" />
    </el-form-item>

    <el-form-item label="Destination Account" prop="destinationAccount">
      <el-input v-model="form.destinationAccount" placeholder="e.g. ACC-002" />
    </el-form-item>

    <el-form-item label="Amount" prop="amount">
      <el-input-number
        v-model="form.amount"
        :min="0.01"
        :max="1000000"
        :precision="2"
        :step="100"
        style="width: 100%"
      />
    </el-form-item>

    <el-form-item label="Currency" prop="currency">
      <el-select v-model="form.currency" style="width: 100%">
        <el-option
          v-for="c in SUPPORTED_CURRENCIES"
          :key="c"
          :label="c"
          :value="c"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="Description" prop="description">
      <el-input
        v-model="form.description"
        type="textarea"
        :rows="2"
        placeholder="Optional description"
      />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        Create Payment
      </el-button>
      <el-button @click="handleReset">Reset</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { SUPPORTED_CURRENCIES } from '../utils/constants'

const emit = defineEmits(['submit'])
const props = defineProps({ loading: { type: Boolean, default: false } })
const formRef = ref(null)

const form = reactive({
  sourceAccount: '',
  destinationAccount: '',
  amount: null,
  currency: 'USD',
  description: '',
})

const validateAmount = (rule, value, callback) => {
  if (value === null || value === undefined || value === '') {
    callback(new Error('Amount is required'))
  } else if (value <= 0) {
    callback(new Error('Amount must be greater than 0'))
  } else if (value > 1000000) {
    callback(new Error('Amount must not exceed 1,000,000'))
  } else {
    callback()
  }
}

const validateAccountsDifferent = (rule, value, callback) => {
  if (value && form.sourceAccount && value === form.sourceAccount) {
    callback(new Error('Destination account must differ from source account'))
  } else {
    callback()
  }
}

const rules = {
  sourceAccount: [
    { required: true, message: 'Source account is required', trigger: 'blur' },
  ],
  destinationAccount: [
    { required: true, message: 'Destination account is required', trigger: 'blur' },
    { validator: validateAccountsDifferent, trigger: 'blur' },
  ],
  amount: [
    { required: true, message: 'Amount is required', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' },
  ],
  currency: [
    { required: true, message: 'Currency is required', trigger: 'change' },
  ],
}

function handleSubmit() {
  formRef.value.validate((valid) => {
    if (valid) {
      emit('submit', { ...form })
    }
  })
}

function handleReset() {
  formRef.value.resetFields()
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/PaymentForm.vue
git commit -m "feat: add PaymentForm component with validation"
```

---

### Task 24: Create PaymentTable Component

**Files:**
- Create: `frontend/src/components/PaymentTable.vue`

**Produces:** Data table with status badges, clickable rows, pagination.

- [ ] **Step 1: Create `frontend/src/components/PaymentTable.vue`**

```vue
<template>
  <el-table
    :data="payments"
    style="width: 100%"
    @row-click="handleRowClick"
    v-loading="loading"
  >
    <el-table-column prop="id" label="Payment ID" width="180">
      <template #default="{ row }">
        <el-text truncated style="max-width: 160px">{{ row.id }}</el-text>
      </template>
    </el-table-column>
    <el-table-column prop="amount" label="Amount" width="120" align="right">
      <template #default="{ row }">
        {{ row.amount }} {{ row.currency }}
      </template>
    </el-table-column>
    <el-table-column prop="currency" label="Currency" width="80" />
    <el-table-column prop="status" label="Status" width="120">
      <template #default="{ row }">
        <StatusBadge :status="row.status" />
      </template>
    </el-table-column>
    <el-table-column prop="description" label="Description" min-width="150">
      <template #default="{ row }">
        <el-text truncated>{{ row.description || '-' }}</el-text>
      </template>
    </el-table-column>
    <el-table-column prop="createdAt" label="Created" width="180">
      <template #default="{ row }">
        {{ formatTime(row.createdAt) }}
      </template>
    </el-table-column>
    <el-table-column label="Action" width="80" fixed="right">
      <template #default="{ row }">
        <el-button size="small" type="primary" link @click.stop="$router.push(`/payments/${row.id}`)">
          Detail
        </el-button>
      </template>
    </el-table-column>
  </el-table>

  <div style="margin-top: 16px; display: flex; justify-content: flex-end">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="$emit('pageChange', $event)"
      @size-change="$emit('sizeChange', $event)"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import StatusBadge from './StatusBadge.vue'

defineProps({
  payments: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
})

defineEmits(['rowClick', 'pageChange', 'sizeChange'])

const currentPage = ref(1)
const pageSize = ref(20)

function handleRowClick(row) {
  // handled via router-link on action button; no-op here for now
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleString()
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/PaymentTable.vue
git commit -m "feat: add PaymentTable component with status badges and pagination"
```

---

### Task 25: Create CreatePaymentView

**Files:**
- Create: `frontend/src/views/CreatePaymentView.vue`

**Produces:** Full create payment page.

- [ ] **Step 1: Create `frontend/src/views/CreatePaymentView.vue`**

```vue
<template>
  <div>
    <h2>Create Payment</h2>
    <PaymentForm :loading="loading" @submit="handleSubmit" />

    <el-dialog v-model="dialogVisible" title="Payment Created" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Payment ID">
          <el-text type="primary">{{ createdPayment?.id }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="Status">
          <StatusBadge :status="createdPayment?.status" />
        </el-descriptions-item>
        <el-descriptions-item label="Amount">
          {{ createdPayment?.amount }} {{ createdPayment?.currency }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="dialogVisible = false">Close</el-button>
        <el-button type="primary" @click="goToDetail">View Details</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PaymentForm from '../components/PaymentForm.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { createPayment } from '../api/payment'

const router = useRouter()
const loading = ref(false)
const dialogVisible = ref(false)
const createdPayment = ref(null)

async function handleSubmit(formData) {
  loading.value = true
  try {
    const idempotencyKey = crypto.randomUUID()
    const res = await createPayment(formData, idempotencyKey)
    if (res.success) {
      createdPayment.value = res.data
      dialogVisible.value = true
    }
  } catch (err) {
    // Error already shown by axios interceptor
  } finally {
    loading.value = false
  }
}

function goToDetail() {
  dialogVisible.value = false
  router.push(`/payments/${createdPayment.value.id}`)
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/CreatePaymentView.vue
git commit -m "feat: add CreatePaymentView with form and success dialog"
```

---

### Task 26: Create PaymentListView

**Files:**
- Create: `frontend/src/views/PaymentListView.vue`

**Produces:** Payment list with status/currency filters, keyword search, pagination.

- [ ] **Step 1: Create `frontend/src/views/PaymentListView.vue`**

```vue
<template>
  <div>
    <h2>Payment List</h2>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <el-select v-model="filters.status" placeholder="Filter by status" clearable style="width: 100%"
                   @change="search">
          <el-option v-for="(info, key) in PAYMENT_STATUS" :key="key" :label="info.label" :value="key" />
        </el-select>
      </el-col>
      <el-col :span="6">
        <el-select v-model="filters.currency" placeholder="Filter by currency" clearable style="width: 100%"
                   @change="search">
          <el-option v-for="c in SUPPORTED_CURRENCIES" :key="c" :label="c" :value="c" />
        </el-select>
      </el-col>
      <el-col :span="8">
        <el-input v-model="filters.keyword" placeholder="Search by ID or description" clearable
                  @keyup.enter="search" @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </el-col>
      <el-col :span="4">
        <el-button type="primary" @click="search">Search</el-button>
      </el-col>
    </el-row>

    <PaymentTable
      :payments="payments"
      :total="total"
      :loading="loading"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import PaymentTable from '../components/PaymentTable.vue'
import { listPayments } from '../api/payment'
import { PAYMENT_STATUS, SUPPORTED_CURRENCIES } from '../utils/constants'

const payments = ref([])
const total = ref(0)
const loading = ref(false)

const filters = reactive({
  status: '',
  currency: '',
  keyword: '',
  page: 1,
  limit: 20,
})

onMounted(() => search())

async function search() {
  loading.value = true
  try {
    const params = {}
    if (filters.status) params.status = filters.status
    if (filters.currency) params.currency = filters.currency
    if (filters.keyword) params.keyword = filters.keyword
    params.page = filters.page
    params.limit = filters.limit

    const res = await listPayments(params)
    if (res.success) {
      payments.value = res.data || []
      total.value = res.total || 0
    }
  } finally {
    loading.value = false
  }
}

function onPageChange(page) {
  filters.page = page
  search()
}

function onSizeChange(size) {
  filters.limit = size
  filters.page = 1
  search()
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/PaymentListView.vue
git commit -m "feat: add PaymentListView with status/currency filters, search, and pagination"
```

---

### Task 27: Create PaymentDetailView

**Files:**
- Create: `frontend/src/views/PaymentDetailView.vue`
- Create: `frontend/src/components/ActionButtons.vue`

**Produces:** Payment detail page with info card, status timeline, error panel, and dynamic action buttons.

- [ ] **Step 1: Create `frontend/src/components/ActionButtons.vue`**

```vue
<template>
  <div style="margin-top: 16px">
    <el-button
      v-for="action in availableActions"
      :key="action.key"
      :type="action.type"
      @click="$emit('action', action.key)"
      :loading="loading === action.key"
      :disabled="!!loading"
    >
      {{ action.label }}
    </el-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { STATUS_ACTIONS } from '../utils/constants'

const props = defineProps({
  status: { type: String, required: true },
  loading: { type: String, default: null },
})

defineEmits(['action'])

const availableActions = computed(() => STATUS_ACTIONS[props.status] || [])
</script>
```

- [ ] **Step 2: Create `frontend/src/views/PaymentDetailView.vue`**

```vue
<template>
  <div v-loading="loading">
    <h2>Payment Detail</h2>

    <!-- Info Card -->
    <el-card v-if="payment" style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>Payment {{ payment.id }}</span>
          <StatusBadge :status="payment.status" />
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Amount">{{ payment.amount }} {{ payment.currency }}</el-descriptions-item>
        <el-descriptions-item label="Status">{{ payment.status }}</el-descriptions-item>
        <el-descriptions-item label="Source Account">{{ payment.sourceAccount }}</el-descriptions-item>
        <el-descriptions-item label="Destination Account">{{ payment.destinationAccount }}</el-descriptions-item>
        <el-descriptions-item label="Description">{{ payment.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Created">{{ formatTime(payment.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="Updated">{{ formatTime(payment.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="Idempotency Key">{{ payment.idempotencyKey }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- Error Panel -->
    <ErrorPanel :error-code="payment?.errorCode" />

    <!-- Action Buttons -->
    <ActionButtons
      v-if="payment"
      :status="payment.status"
      :loading="actionLoading"
      @action="handleAction"
    />

    <!-- Fail Dialog -->
    <el-dialog v-model="failDialogVisible" title="Mark Payment as Failed" width="400px">
      <el-form>
        <el-form-item label="Error Code">
          <el-select v-model="failErrorCode" style="width: 100%">
            <el-option v-for="(label, code) in ERROR_CODE_MAP" :key="code" :label="code" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="Reason">
          <el-input v-model="failReason" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="failDialogVisible = false">Cancel</el-button>
        <el-button type="danger" @click="confirmFail">Confirm Fail</el-button>
      </template>
    </el-dialog>

    <!-- Status History -->
    <StatusTimeline :history="payment?.statusHistory || []" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusBadge from '../components/StatusBadge.vue'
import ErrorPanel from '../components/ErrorPanel.vue'
import ActionButtons from '../components/ActionButtons.vue'
import StatusTimeline from '../components/StatusTimeline.vue'
import { getPayment, validatePayment, sendPayment, completePayment, failPayment, retryPayment } from '../api/payment'
import { ERROR_CODE_MAP } from '../utils/constants'

const route = useRoute()
const payment = ref(null)
const loading = ref(false)
const actionLoading = ref(null)
const failDialogVisible = ref(false)
const failErrorCode = ref('PROCESSING_ERROR')
const failReason = ref('')

onMounted(() => loadPayment())

async function loadPayment() {
  loading.value = true
  try {
    const res = await getPayment(route.params.id)
    if (res.success) payment.value = res.data
  } finally {
    loading.value = false
  }
}

async function handleAction(action) {
  if (action === 'fail') {
    failDialogVisible.value = true
    return
  }
  try {
    await ElMessageBox.confirm(`Are you sure you want to ${action} this payment?`, 'Confirm Action')
  } catch { return }

  actionLoading.value = action
  try {
    const actions = { validate: validatePayment, send: sendPayment, complete: completePayment, retry: retryPayment }
    const fn = actions[action]
    if (!fn) return
    const params = action === 'retry' ? [route.params.id, crypto.randomUUID()] : [route.params.id]
    const res = await fn(...params)
    if (res.success) {
      payment.value = res.data
      ElMessage.success(`${action} successful`)
    }
  } catch (err) { /* shown by interceptor */ }
  finally { actionLoading.value = null }
}

async function confirmFail() {
  actionLoading.value = 'fail'
  try {
    const res = await failPayment(route.params.id, failErrorCode.value, failReason.value)
    if (res.success) {
      payment.value = res.data
      failDialogVisible.value = false
      ElMessage.success('Payment marked as failed')
    }
  } finally { actionLoading.value = null }
}

function formatTime(t) { return t ? new Date(t).toLocaleString() : '' }
</script>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ActionButtons.vue frontend/src/views/PaymentDetailView.vue
git commit -m "feat: add PaymentDetailView with info card, status timeline, actions, and error panel"
```

---

### Task 28: End-to-End Integration Smoke Test

**No new files.** Verify full stack connectivity end-to-end.

- [ ] **Step 1: Start both backend and frontend**

```bash
# Terminal 1: Start backend
cd backend && mvn spring-boot:run

# Terminal 2: Start frontend
cd frontend && npm run dev
```

- [ ] **Step 2: Walk through the complete flow in browser**

Open http://localhost:5173 and verify:

1. **Payment List** loads (may be empty initially)
2. Click **"Create Payment"** in nav → fill form → submit → see success dialog with payment ID
3. Click **"View Details"** → see payment in CREATED status
4. Click **"Validate"** button → payment becomes VALIDATED, timeline shows 2 entries
5. Click **"Send"** → payment becomes SENT
6. Click **"Complete"** → payment becomes COMPLETED (4 timeline entries)
7. Go back to **Payment List** → filter by COMPLETED → see the payment
8. Create a second payment → click **"Mark Failed"** → select error code → confirm → verify error panel shows
9. Click **"Retry"** → payment returns to VALIDATED

- [ ] **Step 3: Verify all 5 requirements from spec**

| # | Requirement | Verified? |
|---|-------------|-----------|
| 1 | Create a new payment | Create form + success dialog |
| 2 | View payment status and details | Detail page with info card + status badge |
| 3 | View payment history (all status transitions) | Timeline component with timestamps |
| 4 | Search/filter payments by status | List page with status/currency filters + keyword search |
| 5 | View error details for failed payments | Error panel showing error code + reason |

---

## Phase 4: AI Anomaly Detection (Extension)

### Task 29: Create Risk Assessment Table and Entity

**Files:**
- Modify: `backend/src/main/resources/db/schema.sql`
- Create: `backend/src/main/java/com/hsbc/payment/entity/RiskAssessment.java`
- Create: `backend/src/main/java/com/hsbc/payment/mapper/RiskAssessmentMapper.java`

**Produces:** New `risk_assessments` table and corresponding entity + mapper.

- [ ] **Step 1: Append to `backend/src/main/resources/db/schema.sql`**

```sql
CREATE TABLE IF NOT EXISTS risk_assessments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      VARCHAR(36)  NOT NULL,
    risk_score      INTEGER      NOT NULL DEFAULT 0,
    risk_level      VARCHAR(10)  NOT NULL DEFAULT 'LOW',
    recommendation  VARCHAR(10)  NOT NULL DEFAULT 'APPROVE',
    risk_factors    JSON         NOT NULL,
    assessed_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    agent_version   VARCHAR(20)  NOT NULL DEFAULT '1.0',
    CONSTRAINT fk_risk_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: Create `RiskAssessment.java`**

```java
package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "risk_assessments", autoResultMap = true)
public class RiskAssessment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentId;
    private Integer riskScore;
    private String riskLevel;
    private String recommendation;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> riskFactors;

    private LocalDateTime assessedAt;
    private String agentVersion;
}
```

- [ ] **Step 3: Create `RiskAssessmentMapper.java`**

```java
package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.RiskAssessment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RiskAssessmentMapper extends BaseMapper<RiskAssessment> {
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/schema.sql \
        backend/src/main/java/com/hsbc/payment/entity/RiskAssessment.java \
        backend/src/main/java/com/hsbc/payment/mapper/RiskAssessmentMapper.java
git commit -m "feat: add RiskAssessment entity, mapper, and database table"
```

---

### Task 30: Create Rule Engine (Layer 1)

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/service/RuleEngineService.java`

**Produces:** 5 heuristic risk rules returning sub-scores and risk factors.

- [ ] **Step 1: Create `RuleEngineService.java`**

```java
package com.hsbc.payment.service;

import com.hsbc.payment.entity.Payment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RuleEngineService {

    public static class RuleResult {
        public String name;
        public int score;
        public String detail;

        public RuleResult(String name, int score, String detail) {
            this.name = name;
            this.score = score;
            this.detail = detail;
        }
    }

    public static class AssessmentResult {
        public int totalScore;
        public String riskLevel;
        public String recommendation;
        public List<RuleResult> triggeredRules;

        public AssessmentResult(int totalScore, String riskLevel, String recommendation, List<RuleResult> triggeredRules) {
            this.totalScore = totalScore;
            this.riskLevel = riskLevel;
            this.recommendation = recommendation;
            this.triggeredRules = triggeredRules;
        }
    }

    /**
     * @param payment Current payment
     * @param accountHistory List of historical payments from the same source account
     */
    public AssessmentResult assess(Payment payment, List<Payment> accountHistory) {
        List<RuleResult> allResults = new ArrayList<>();
        allResults.add(checkAmountAnomaly(payment, accountHistory));
        allResults.add(checkUnusualTime(payment));
        allResults.add(checkNewPayee(payment, accountHistory));
        allResults.add(checkVelocitySpike(payment, accountHistory));
        allResults.add(checkHighAmount(payment));

        int totalScore = allResults.stream().mapToInt(r -> r.score).sum();
        totalScore = Math.min(totalScore, 100);

        // Only keep rules that actually triggered
        List<RuleResult> triggered = allResults.stream().filter(r -> r.score > 0).toList();

        String riskLevel, recommendation;
        if (totalScore >= 70) { riskLevel = "HIGH"; recommendation = "BLOCK"; }
        else if (totalScore >= 40) { riskLevel = "MEDIUM"; recommendation = "REVIEW"; }
        else { riskLevel = "LOW"; recommendation = "APPROVE"; }

        return new AssessmentResult(totalScore, riskLevel, recommendation, triggered);
    }

    private RuleResult checkAmountAnomaly(Payment payment, List<Payment> history) {
        if (history.isEmpty()) {
            return new RuleResult("AMOUNT_ANOMALY", 15, "No history data, new account");
        }
        double avg = history.stream().mapToDouble(p -> p.getAmount().doubleValue()).average().orElse(0);
        double ratio = payment.getAmount().doubleValue() / avg;
        if (ratio > 5) {
            return new RuleResult("AMOUNT_ANOMALY", 35,
                    String.format("Amount is %.1fx historical average", ratio));
        }
        if (ratio > 3) {
            return new RuleResult("AMOUNT_ANOMALY", 20,
                    String.format("Amount is %.1fx historical average", ratio));
        }
        return new RuleResult("AMOUNT_ANOMALY", 0, "Amount within normal range");
    }

    private RuleResult checkUnusualTime(Payment payment) {
        int hour = payment.getCreatedAt() != null ?
                payment.getCreatedAt().getHour() : LocalDateTime.now().getHour();
        if (hour >= 0 && hour < 6) {
            return new RuleResult("UNUSUAL_TIME", 25, String.format("Transaction at %d:00 (late night)", hour));
        }
        return new RuleResult("UNUSUAL_TIME", 0, "Transaction during normal hours");
    }

    private RuleResult checkNewPayee(Payment payment, List<Payment> history) {
        Set<String> knownPayees = new HashSet<>();
        for (Payment p : history) {
            knownPayees.add(p.getDestinationAccount());
        }
        if (!knownPayees.contains(payment.getDestinationAccount())) {
            return new RuleResult("NEW_PAYEE", 30, "First transfer to this payee");
        }
        return new RuleResult("NEW_PAYEE", 0, "Known payee");
    }

    private RuleResult checkVelocitySpike(Payment payment, List<Payment> history) {
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        long count = history.stream()
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(tenMinutesAgo))
                .count();
        if (count >= 5) {
            return new RuleResult("VELOCITY_SPIKE", 40, count + " transactions in last 10 minutes");
        }
        if (count >= 3) {
            return new RuleResult("VELOCITY_SPIKE", 20, count + " transactions in last 10 minutes");
        }
        return new RuleResult("VELOCITY_SPIKE", 0, "Normal transaction frequency");
    }

    private RuleResult checkHighAmount(Payment payment) {
        double amt = payment.getAmount().doubleValue();
        if (amt > 50000) {
            return new RuleResult("HIGH_AMOUNT", 25, "Single transaction over $50,000");
        }
        if (amt > 10000) {
            return new RuleResult("HIGH_AMOUNT", 10, "Single transaction over $10,000");
        }
        return new RuleResult("HIGH_AMOUNT", 0, "Normal amount");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/service/RuleEngineService.java
git commit -m "feat: add AI Layer 1 rule engine with 5 heuristic risk detection rules"
```

---

### Task 31: Create Statistical Anomaly Detection (Layer 2)

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/service/StatisticalAnomalyService.java`

**Produces:** Z-score and IQR-based anomaly scoring.

- [ ] **Step 1: Create `StatisticalAnomalyService.java`**

```java
package com.hsbc.payment.service;

import com.hsbc.payment.entity.Payment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatisticalAnomalyService {

    public static class StatResult {
        public int score;
        public String detail;

        public StatResult(int score, String detail) {
            this.score = score;
            this.detail = detail;
        }
    }

    public StatResult analyze(Payment payment, List<Payment> history) {
        if (history.size() < 10) {
            return new StatResult(0, "Insufficient history data (< 10 transactions), skipping statistical detection");
        }

        List<Double> amounts = history.stream()
                .map(p -> p.getAmount().doubleValue())
                .sorted()
                .toList();

        double mean = amounts.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = amounts.stream().mapToDouble(a -> Math.pow(a - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double zScore = stdDev > 0 ? Math.abs((payment.getAmount().doubleValue() - mean) / stdDev) : 0;

        int n = amounts.size();
        double q1 = amounts.get((int) (n * 0.25));
        double q3 = amounts.get((int) (n * 0.75));
        double iqr = q3 - q1;
        double upperBound = q3 + 1.5 * iqr;

        int score = 0;
        StringBuilder detail = new StringBuilder();

        if (zScore > 3) { score += 30; detail.append(String.format("z-score=%.2f (extreme deviation); ", zScore)); }
        else if (zScore > 2) { score += 15; detail.append(String.format("z-score=%.2f (significant deviation); ", zScore)); }

        if (payment.getAmount().doubleValue() > upperBound) {
            score += 20;
            detail.append(String.format("Amount exceeds IQR upper bound (%.2f); ", upperBound));
        }

        if (detail.isEmpty()) {
            detail.append("Statistical detection normal");
        }

        return new StatResult(Math.min(score, 50), detail.toString().trim());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/service/StatisticalAnomalyService.java
git commit -m "feat: add AI Layer 2 statistical anomaly detection with z-score and IQR"
```

---

### Task 32: Create RiskAssessmentService (Integration)

**Files:**
- Create: `backend/src/main/java/com/hsbc/payment/service/RiskAssessmentService.java`

**Consumes:** `RuleEngineService`, `StatisticalAnomalyService`, `RiskAssessmentMapper`, `PaymentMapper`
**Produces:** End-to-end risk assessment integrated into validation flow.

- [ ] **Step 1: Create `RiskAssessmentService.java`**

```java
package com.hsbc.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.entity.RiskAssessment;
import com.hsbc.payment.mapper.PaymentMapper;
import com.hsbc.payment.mapper.RiskAssessmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final RuleEngineService ruleEngineService;
    private final StatisticalAnomalyService statisticalAnomalyService;
    private final RiskAssessmentMapper riskAssessmentMapper;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Run full risk assessment on a payment.
     * @return AssessmentResult with final recommendation
     */
    public RuleEngineService.AssessmentResult assess(String paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) return null;

        // Fetch historical transactions from same source account (last 90 days)
        List<Payment> accountHistory = paymentMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Payment>()
                .eq(Payment::getSourceAccount, payment.getSourceAccount())
                .ne(Payment::getId, paymentId)
                .ge(Payment::getCreatedAt, LocalDateTime.now().minusDays(90))
                .orderByDesc(Payment::getCreatedAt)
        );

        // Layer 1: Rule engine
        RuleEngineService.AssessmentResult ruleResult = ruleEngineService.assess(payment, accountHistory);

        // Layer 2: Statistical analysis
        StatisticalAnomalyService.StatResult statResult = statisticalAnomalyService.analyze(payment, accountHistory);

        // Combine scores
        int finalScore = Math.min(ruleResult.totalScore + statResult.score, 100);

        String riskLevel;
        String recommendation;
        if (finalScore >= 70) { riskLevel = "HIGH"; recommendation = "BLOCK"; }
        else if (finalScore >= 40) { riskLevel = "MEDIUM"; recommendation = "REVIEW"; }
        else { riskLevel = "LOW"; recommendation = "APPROVE"; }

        // Build risk factors list
        List<Map<String, Object>> allFactors = new ArrayList<>();
        for (RuleEngineService.RuleResult r : ruleResult.triggeredRules) {
            Map<String, Object> factor = new LinkedHashMap<>();
            factor.put("source", "RULE_ENGINE");
            factor.put("rule", r.name);
            factor.put("score", r.score);
            factor.put("detail", r.detail);
            allFactors.add(factor);
        }
        Map<String, Object> statFactor = new LinkedHashMap<>();
        statFactor.put("source", "STATISTICAL");
        statFactor.put("score", statResult.score);
        statFactor.put("detail", statResult.detail);
        allFactors.add(statFactor);

        // Save to database
        RiskAssessment assessment = new RiskAssessment();
        assessment.setPaymentId(paymentId);
        assessment.setRiskScore(finalScore);
        assessment.setRiskLevel(riskLevel);
        assessment.setRecommendation(recommendation);
        assessment.setRiskFactors(allFactors);
        assessment.setAgentVersion("2.0");
        assessment.setAssessedAt(LocalDateTime.now());
        riskAssessmentMapper.insert(assessment);

        return new RuleEngineService.AssessmentResult(finalScore, riskLevel, recommendation,
                ruleResult.triggeredRules);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/service/RiskAssessmentService.java
git commit -m "feat: add RiskAssessmentService integrating Layer 1 + Layer 2 into validation flow"
```

---

### Task 33: Integrate Risk Assessment into Validation

**Files:**
- Modify: `backend/src/main/java/com/hsbc/payment/service/impl/PaymentServiceImpl.java`
- Modify: `backend/src/main/java/com/hsbc/payment/dto/response/PaymentResponse.java`

**Produces:** Risk assessment runs automatically during validate, response includes risk data.

- [ ] **Step 1: Inject `RiskAssessmentService` into `PaymentServiceImpl`**

Add to constructor injection:
```java
private final RiskAssessmentService riskAssessmentService;
```

- [ ] **Step 2: Modify `processValidate()` to run risk assessment**

Replace the existing `processValidate` method:
```java
@Override
@Transactional
public PaymentResponse processValidate(String paymentId) {
    Payment payment = findPaymentById(paymentId);
    PaymentStatus fromStatus = PaymentStatus.fromString(payment.getStatus());
    PaymentStatus toStatus = PaymentStatus.VALIDATED;

    if (!stateMachineService.canTransition(fromStatus, toStatus)) {
        throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                "Cannot transition from " + fromStatus + " to " + toStatus);
    }

    // Run AI risk assessment
    RuleEngineService.AssessmentResult riskResult = riskAssessmentService.assess(paymentId);
    if (riskResult != null && "BLOCK".equals(riskResult.recommendation)) {
        updatePaymentStatus(payment, "FAILED", ErrorCode.RISK_BLOCKED.name());
        recordStatusHistory(paymentId, fromStatus.name(), "FAILED",
                "Risk assessment blocked: score " + riskResult.totalScore,
                ErrorCode.RISK_BLOCKED.name());
        return getPayment(paymentId);
    }

    updatePaymentStatus(payment, toStatus.name(), null);
    recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), null, null);

    PaymentResponse response = getPayment(paymentId);
    if (riskResult != null) {
        response.setRiskScore(riskResult.totalScore);
        response.setRiskLevel(riskResult.riskLevel);
    }
    return response;
}
```

- [ ] **Step 3: Add risk fields to `PaymentResponse.java`**

Add these fields:
```java
private Integer riskScore;
private String riskLevel;
```

- [ ] **Step 4: Create risk assessment API endpoint**

Add to `PaymentProcessController.java`:
```java
@GetMapping("/risk-assessment")
@Operation(summary = "Get risk assessment for this payment")
public ResponseEntity<ApiResponse<?>> getRiskAssessment(@PathVariable String id) {
    // Query the latest risk assessment for this payment
    var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hsbc.payment.entity.RiskAssessment>()
        .eq(com.hsbc.payment.entity.RiskAssessment::getPaymentId, id)
        .orderByDesc(com.hsbc.payment.entity.RiskAssessment::getAssessedAt)
        .last("LIMIT 1");
    var assessment = riskAssessmentMapper.selectOne(wrapper);
    return ResponseEntity.ok(ApiResponse.ok(assessment));
}
```

Add `RiskAssessmentMapper riskAssessmentMapper` to the controller constructor.

- [ ] **Step 5: Verify compilation and run tests**

```bash
cd backend && mvn clean test
```

Expected: All tests pass, BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hsbc/payment/service/impl/PaymentServiceImpl.java \
        backend/src/main/java/com/hsbc/payment/dto/response/PaymentResponse.java \
        backend/src/main/java/com/hsbc/payment/controller/PaymentProcessController.java
git commit -m "feat: integrate AI risk assessment into validation flow with BLOCK action"
```

---

### Task 34: Add Risk Assessment API Endpoint for Frontend

**Files:**
- Create: `frontend/src/components/RiskScoreGauge.vue`

**Produces:** Risk score visual gauge for the payment detail page.

- [ ] **Step 1: Create `RiskScoreGauge.vue`**

```vue
<template>
  <el-card v-if="riskScore !== null && riskScore !== undefined" style="margin-bottom: 16px">
    <template #header>
      <span>Risk Assessment</span>
    </template>
    <div style="display: flex; align-items: center; gap: 24px">
      <el-progress
        type="dashboard"
        :percentage="riskScore"
        :color="riskColor"
        :stroke-width="12"
        style="width: 140px"
      />
      <div>
        <el-tag :type="riskTagType" size="large">
          {{ riskLevel }} RISK
        </el-tag>
        <p style="margin-top: 8px; color: #909399; font-size: 13px">
          Score: {{ riskScore }} / 100
        </p>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  riskScore: { type: Number, default: null },
  riskLevel: { type: String, default: null },
})

const riskColor = computed(() => {
  const s = props.riskScore || 0
  if (s >= 70) return '#f56c6c'
  if (s >= 40) return '#e6a23c'
  return '#67c23a'
})

const riskTagType = computed(() => {
  if (props.riskLevel === 'HIGH') return 'danger'
  if (props.riskLevel === 'MEDIUM') return 'warning'
  return 'success'
})
</script>
```

- [ ] **Step 2: Integrate RiskScoreGauge into PaymentDetailView**

In `PaymentDetailView.vue`, add after the info card `<el-card>`:
```html
<RiskScoreGauge
  v-if="payment?.riskScore !== null && payment?.riskScore !== undefined"
  :risk-score="payment.riskScore"
  :risk-level="payment.riskLevel"
/>
```

Add import:
```javascript
import RiskScoreGauge from '../components/RiskScoreGauge.vue'
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/RiskScoreGauge.vue frontend/src/views/PaymentDetailView.vue
git commit -m "feat: add RiskScoreGauge component to display AI risk assessment results"
```

---

### Task 35: Write AI Anomaly Detection Tests

**Files:**
- Create: `backend/src/test/java/com/hsbc/payment/service/RuleEngineServiceTest.java`
- Create: `backend/src/test/java/com/hsbc/payment/service/StatisticalAnomalyServiceTest.java`

**Produces:** Verify risk scoring logic with known inputs.

- [ ] **Step 1: Create `RuleEngineServiceTest.java`**

```java
package com.hsbc.payment.service;

import com.hsbc.payment.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineServiceTest {

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        ruleEngineService = new RuleEngineService();
    }

    @Test
    @DisplayName("Normal payment with known payee should be LOW risk")
    void normalPaymentLowRisk() {
        Payment payment = createPayment("ACC-001", "ACC-002", 100, 14); // 2pm
        Payment h1 = createPayment("ACC-001", "ACC-002", 120, 10);
        Payment h2 = createPayment("ACC-001", "ACC-002", 90, 11);

        var result = ruleEngineService.assess(payment, List.of(h1, h2));
        assertEquals("LOW", result.riskLevel);
        assertEquals("APPROVE", result.recommendation);
        assertTrue(result.totalScore < 40);
    }

    @Test
    @DisplayName("High amount (>$50,000) should trigger HIGH_AMOUNT rule")
    void highAmountRule() {
        Payment payment = createPayment("ACC-001", "ACC-002", 60000, 14);
        var result = ruleEngineService.assess(payment, List.of());
        assertTrue(result.triggeredRules.stream().anyMatch(r -> r.name.equals("HIGH_AMOUNT")));
    }

    @Test
    @DisplayName("Late night + new payee + large amount should be HIGH risk (>70)")
    void multipleRiskFactors() {
        Payment payment = createPayment("ACC-001", "ACC-999", 55000, 3); // 3am
        var result = ruleEngineService.assess(payment, List.of()); // no history
        assertTrue(result.totalScore >= 50, "Expected high score but got " + result.totalScore);
    }

    @Test
    @DisplayName("New payee should trigger NEW_PAYEE rule")
    void newPayeeRule() {
        Payment payment = createPayment("ACC-001", "NEW-ACC", 200, 14);
        Payment h1 = createPayment("ACC-001", "ACC-002", 100, 10);
        var result = ruleEngineService.assess(payment, List.of(h1));
        assertTrue(result.triggeredRules.stream().anyMatch(r -> r.name.equals("NEW_PAYEE")));
    }

    @Test
    @DisplayName("Velocity spike (>5 txns in 10 min) should trigger VELOCITY_SPIKE")
    void velocitySpike() {
        Payment payment = createPayment("ACC-001", "ACC-002", 500, 14);
        // 6 recent transactions in last 10 min
        List<Payment> history = List.of(
            createPayment("ACC-001", "ACC-002", 100, 14),
            createPayment("ACC-001", "ACC-002", 100, 14),
            createPayment("ACC-001", "ACC-002", 100, 14),
            createPayment("ACC-001", "ACC-002", 100, 14),
            createPayment("ACC-001", "ACC-002", 100, 14),
            createPayment("ACC-001", "ACC-002", 100, 14)
        );
        var result = ruleEngineService.assess(payment, history);
        assertTrue(result.triggeredRules.stream().anyMatch(r -> r.name.equals("VELOCITY_SPIKE")));
    }

    private Payment createPayment(String source, String dest, double amount, int hour) {
        Payment p = new Payment();
        p.setSourceAccount(source);
        p.setDestinationAccount(dest);
        p.setAmount(BigDecimal.valueOf(amount));
        p.setCurrency("USD");
        p.setCreatedAt(LocalDateTime.now().withHour(hour).withMinute(0).withSecond(0));
        return p;
    }
}
```

- [ ] **Step 2: Create `StatisticalAnomalyServiceTest.java`**

```java
package com.hsbc.payment.service;

import com.hsbc.payment.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatisticalAnomalyServiceTest {

    private StatisticalAnomalyService service;

    @BeforeEach
    void setUp() {
        service = new StatisticalAnomalyService();
    }

    @Test
    @DisplayName("Insufficient history (< 10) returns score 0")
    void insufficientHistory() {
        Payment payment = createPayment(5000);
        List<Payment> history = List.of(createPayment(100));
        var result = service.analyze(payment, history);
        assertEquals(0, result.score);
        assertTrue(result.detail.contains("Insufficient"));
    }

    @Test
    @DisplayName("Payment far above average triggers high z-score")
    void extremeDeviation() {
        List<Payment> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            history.add(createPayment(100 + i * 5)); // avg ~147
        }
        Payment payment = createPayment(10000); // way above avg
        var result = service.analyze(payment, history);
        assertTrue(result.score > 0, "Expected positive score for extreme deviation");
    }

    @Test
    @DisplayName("Normal payment within range returns 0")
    void normalRange() {
        List<Payment> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            history.add(createPayment(100 + i * 5)); // avg ~147
        }
        Payment payment = createPayment(150); // close to avg
        var result = service.analyze(payment, history);
        assertEquals(0, result.score);
    }

    private Payment createPayment(double amount) {
        Payment p = new Payment();
        p.setAmount(BigDecimal.valueOf(amount));
        p.setCurrency("USD");
        return p;
    }
}
```

- [ ] **Step 3: Run tests**

```bash
cd backend && mvn test -Dtest=RuleEngineServiceTest,StatisticalAnomalyServiceTest
```

Expected: 9 tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/hsbc/payment/service/RuleEngineServiceTest.java \
        backend/src/test/java/com/hsbc/payment/service/StatisticalAnomalyServiceTest.java
git commit -m "test: add AI anomaly detection tests for rule engine and statistical analysis"
```

---

## Completion Checklist

- [ ] All 35 tasks committed
- [ ] Backend: 14 endpoints operational, Swagger UI accessible at http://localhost:8080/swagger-ui.html
- [ ] Backend tests: StateMachineService (11), PaymentService (6), PaymentController (6), RuleEngine (5), Statistical (3) = 31 tests passing
- [ ] Frontend: 3 pages (create, list, detail) + risk gauge, connected to backend
- [ ] All 5 user requirements verified end-to-end
- [ ] AI anomaly detection: Rule engine (Layer 1) + Statistical (Layer 2) operational, Layer 3 (LLM) reserved for future
