package com.intellicart.aiassistantservice.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Component
public class OrderServiceClient {

    private static final String ORDER_SVC = "http://localhost:8082";
    private final RestTemplate http = new RestTemplate();

    public List<OrderDto> getOrdersByUser(Long userId) {
        try {
            OrderDto[] arr = http.getForObject(
                    ORDER_SVC + "/api/orders/user/" + userId,
                    OrderDto[].class
            );
            return (arr != null) ? Arrays.asList(arr) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public OrderDto createOrderSimple(Long userId, Long bookId, int quantity) {
        try {
            OrderDto payload = new OrderDto();
            payload.setUserId(userId);

            OrderItemDto item = new OrderItemDto();
            item.setBookId(bookId);
            item.setQuantity(Math.max(1, quantity));

            payload.setItems(List.of(item));

            return http.postForObject(
                    ORDER_SVC + "/api/orders",
                    payload,
                    OrderDto.class
            );
        } catch (Exception e) {
            return null;
        }
    }
}
