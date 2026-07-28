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
    private long total;

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
