package com.intellicart.aiassistantservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class OrderApiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrderApiClient(RestTemplate restTemplate,
                          @Value("${orders.api.base:http://localhost:8082}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public List<OrderDto> getAllOrders() {
        String url = baseUrl + "/api/orders";
        ResponseEntity<OrderDto[]> resp = restTemplate.getForEntity(url, OrderDto[].class);
        OrderDto[] body = resp.getBody();
        return body == null ? Collections.emptyList() : Arrays.asList(body);
    }

    public List<OrderDto> getOrdersByUser(Long userId) {
        String url = baseUrl + "/api/orders/user/" + userId;
        ResponseEntity<OrderDto[]> resp = restTemplate.getForEntity(url, OrderDto[].class);
        OrderDto[] body = resp.getBody();
        return body == null ? Collections.emptyList() : Arrays.asList(body);
    }
}
