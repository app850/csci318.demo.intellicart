package com.intellicart.aiassistantservice.client;

import java.math.BigDecimal;
import java.util.List;

public class OrderDto {
    private Long id;
    private Long userId;
    private BigDecimal totalAmount;
    private List<OrderItemDto> items;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }
}
