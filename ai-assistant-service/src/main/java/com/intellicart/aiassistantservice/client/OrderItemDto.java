package com.intellicart.aiassistantservice.client;

import java.math.BigDecimal;

public class OrderItemDto {
    private Long id;
    private Long bookId;
    private int quantity;
    private BigDecimal price;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
