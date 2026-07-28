package com.hsbc.payment.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.ToString;

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

    @NotBlank(message = "Source account password is required")
    @Size(max = 128, message = "Password must not exceed 128 characters")
    @ToString.Exclude
    private String sourceAccountPassword;

    @NotBlank(message = "Recipient surname is required")
    @Size(max = 50, message = "Recipient surname must not exceed 50 characters")
    private String recipientLastName;
}
