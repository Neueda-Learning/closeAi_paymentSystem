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
