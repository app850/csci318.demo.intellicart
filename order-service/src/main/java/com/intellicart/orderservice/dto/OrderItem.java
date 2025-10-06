package com.intellicart.orderservice.dto;

import java.math.BigDecimal;

public record OrderItem(
        Long bookId,
        String title,
        int quantity,
        BigDecimal price
) {}