package com.intellicart.aiassistantservice.tools;

import com.intellicart.aiassistantservice.convo.ToolResult;
import com.intellicart.aiassistantservice.convo.TurnContext;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component("orderTool")
public class OrderTools implements Tool {

    private static final String ORDER_SVC = "http://localhost:8082";
    private final RestTemplate http = new RestTemplate();

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult handle(String action, Map<String, Object> args, TurnContext ctx) {
        try {
            switch (action) {
                case "listOrdersForUser" -> {
                    Long uid = argOrCtxUserId(args.get("userId"), ctx);
                    if (uid == null) return ToolResult.err("userId is required");
                    Object orders = getJson(ORDER_SVC + "/api/orders/user/" + uid);
                    return ToolResult.ok(Map.of("orders", orders));
                }
                case "checkout" -> {
                    Long uid = argOrCtxUserId(args.get("userId"), ctx);
                    if (uid == null) return ToolResult.err("userId is required");
                    Map<String, Object> order = placeOrder(uid, ctx.cart());
                    ctx.clearCart();
                    return ToolResult.ok(Map.of("order", order));
                }
                default -> { return ToolResult.err("unsupported order action: " + action); }
            }
        } catch (Exception e) {
            return ToolResult.err(e.getMessage());
        }
    }

    private Long argOrCtxUserId(Object arg, TurnContext ctx) {
        if (arg instanceof Number n) return n.longValue();
        if (ctx.userId() != null) return ctx.userId();
        try { return arg == null ? null : Long.parseLong(String.valueOf(arg)); } catch (Exception ignored) { return null; }
    }

    private Object getJson(String url) {
        ResponseEntity<Object> resp = http.getForEntity(url, Object.class);
        return resp.getBody();
    }

    private Map<String, Object> placeOrder(Long userId, List<Map<String, Object>> cart) {
        List<Map<String, Object>> items = new ArrayList<>();
        double total = 0.0;
        for (Map<String, Object> c : cart) {
            long bookId = toLong(c.get("bookId"), 0L);
            int quantity = (int) toLong(c.getOrDefault("quantity", 1), 1L);
            double price = toDouble(c.get("price"), 0.0);
            items.add(Map.of("bookId", bookId, "quantity", quantity, "price", price));
            total += price * quantity;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("items", items);
        payload.put("totalAmount", total);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<Object> resp = http.postForEntity(
                    ORDER_SVC + "/api/orders",
                    new HttpEntity<>(payload, headers),
                    Object.class
            );
            Object body = resp.getBody();
            if (body instanceof Map<?, ?> m) return (Map<String, Object>) m;
        } catch (Exception ignored) {}
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("id", "unknown");
        fallback.put("totalAmount", total);
        fallback.put("items", items);
        return fallback;
    }

    private long toLong(Object v, long d) {
        if (v == null) return d;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return d; }
    }

    private double toDouble(Object v, double d) {
        if (v == null) return d;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return d; }
    }
}
