package com.intellicart.orderservice.service;

import com.intellicart.orderservice.domain.CustomerOrder;
import com.intellicart.orderservice.infrastructure.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

// We no longer need imports for StreamBridge or the event DTOs

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    // The StreamBridge field has been removed

    @Autowired
    public OrderService(OrderRepository orderRepository) { // StreamBridge removed from constructor
        this.orderRepository = orderRepository;
    }

    public CustomerOrder createOrder(CustomerOrder customerOrder) {
        // This line correctly sets up the database relationship and is still needed
        customerOrder.getItems().forEach(item -> item.setOrder(customerOrder));

        // We now just save the order and return it. All event publishing code is gone.
        return orderRepository.save(customerOrder);
    }

    public List<CustomerOrder> findAllOrders() {
        return orderRepository.findAll();
    }

    public List<CustomerOrder> findOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }
}