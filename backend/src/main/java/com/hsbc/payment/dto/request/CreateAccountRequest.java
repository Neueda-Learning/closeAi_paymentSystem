package com.hsbc.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

@Data
public class CreateAccountRequest {

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^ACC-\\d{3,10}$", message = "Account number must match ACC- followed by 3 to 10 digits")
    private String accountNumber;

    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name must not exceed 100 characters")
    private String accountName;

    @NotNull(message = "Initial balance is required")
    @DecimalMin(value = "0.00", message = "Initial balance must not be negative")
    @Digits(integer = 13, fraction = 2, message = "Initial balance must have at most 13 integer digits and 2 decimal places")
    private BigDecimal balance;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^(USD|EUR|GBP|CNY)$", message = "Currency must be one of USD, EUR, GBP or CNY")
    private String currency;

    @NotBlank(message = "Account holder surname is required")
    @Size(max = 50, message = "Account holder surname must not exceed 50 characters")
    private String holderLastName;

    @NotBlank(message = "Account password is required")
    @Size(min = 8, max = 128, message = "Password must contain between 8 and 128 characters")
    @ToString.Exclude
    private String password;
}
