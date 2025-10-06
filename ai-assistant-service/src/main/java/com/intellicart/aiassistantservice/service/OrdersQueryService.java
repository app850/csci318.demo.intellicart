package com.intellicart.aiassistantservice.service;

import com.intellicart.aiassistantservice.client.OrderApiClient;
import com.intellicart.aiassistantservice.client.OrderDto;
import com.intellicart.aiassistantservice.client.OrderItemDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OrdersQueryService {

    private static final Pattern USER_ORDERS =
            Pattern.compile("(?i)\\b(show|list|get)\\s+orders\\s+for\\s+user\\s+(\\d+)\\b");
    private static final Pattern ALL_ORDERS =
            Pattern.compile("(?i)\\b(list|show|get)\\s+all\\s+orders\\b");

    private final OrderApiClient client;

    public OrdersQueryService(OrderApiClient client) {
        this.client = client;
    }

    /** Returns non-null when this service can answer the message */
    public String tryAnswer(String message) {
        if (message == null || message.isBlank()) return null;

        Matcher m1 = USER_ORDERS.matcher(message);
        if (m1.find()) {
            Long userId = Long.parseLong(m1.group(2));
            return formatUserOrders(userId, client.getOrdersByUser(userId));
        }

        Matcher m2 = ALL_ORDERS.matcher(message);
        if (m2.find()) {
            return formatAllOrders(client.getAllOrders());
        }

        return null; // not handled
    }

    private String formatUserOrders(Long userId, List<OrderDto> orders) {
        if (orders.isEmpty()) {
            return "Orders for user " + userId + ": []";
        }
        StringBuilder sb = new StringBuilder("Orders for user ").append(userId).append(":\n");
        for (OrderDto o : orders) {
            int itemCount = o.getItems() == null ? 0 : o.getItems().size();
            sb.append("- #").append(o.getId())
                    .append(" total ").append(o.getTotalAmount())
                    .append(" (").append(itemCount).append(itemCount == 1 ? " item" : " items").append(")");
            if (itemCount > 0) {
                sb.append("\n  items:");
                for (OrderItemDto it : o.getItems()) {
                    sb.append("\n   • book ").append(it.getBookId())
                            .append(" x").append(it.getQuantity())
                            .append(" @ ").append(it.getPrice());
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatAllOrders(List<OrderDto> orders) {
        if (orders.isEmpty()) return "Orders: []";
        StringBuilder sb = new StringBuilder("Orders:\n");
        for (OrderDto o : orders) {
            int itemCount = o.getItems() == null ? 0 : o.getItems().size();
            sb.append("- #").append(o.getId())
                    .append(" user ").append(o.getUserId())
                    .append(" total ").append(o.getTotalAmount())
                    .append(" (").append(itemCount).append(itemCount == 1 ? " item" : " items").append(")\n");
        }
        return sb.toString();
    }
}
