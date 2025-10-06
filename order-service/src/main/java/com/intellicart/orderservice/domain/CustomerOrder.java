package com.intellicart.orderservice.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_order")   // 👈 matches import.sql
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    // --- helpers to keep both sides in sync ---
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        recalcTotal();
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
        recalcTotal();
    }

    public void recalcTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem it : items) {
            if (it.getPrice() != null) {
                sum = sum.add(it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
            }
        }
        this.totalAmount = sum;
    }

    // --- getters/setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) {
        this.items.clear();
        if (items != null) {
            items.forEach(this::addItem); // keeps both sides in sync + updates total
        }
    }
}
