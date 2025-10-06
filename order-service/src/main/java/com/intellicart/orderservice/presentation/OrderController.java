package com.intellicart.orderservice.presentation;

import com.intellicart.orderservice.domain.CustomerOrder;
import com.intellicart.orderservice.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public CustomerOrder createOrder(@RequestBody CustomerOrder customerOrder) {
        return orderService.createOrder(customerOrder);
    }

    @GetMapping
    public List<CustomerOrder> getAllOrders() {
        return orderService.findAllOrders();
    }

    @GetMapping("/user/{userId}")
    public List<CustomerOrder> getOrdersByUserId(@PathVariable Long userId) {
        return orderService.findOrdersByUserId(userId);
    }
}
