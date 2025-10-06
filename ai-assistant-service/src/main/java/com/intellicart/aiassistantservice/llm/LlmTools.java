package com.intellicart.aiassistantservice.llm;

import com.intellicart.aiassistantservice.client.BookServiceClient;
import com.intellicart.aiassistantservice.client.OrderDto;
import com.intellicart.aiassistantservice.client.OrderServiceClient;
import com.intellicart.aiassistantservice.client.UserServiceClient;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LlmTools {

    private final UserServiceClient users;
    private final OrderServiceClient orders;
    private final BookServiceClient books;

    public LlmTools(UserServiceClient users, OrderServiceClient orders, BookServiceClient books) {
        this.users = users;
        this.orders = orders;
        this.books = books;
    }

    @Tool("Find a user by username (case-insensitive). Return null if not found.")
    public UserServiceClient.UserDto find_user_by_name(String username) {
        if (username == null) return null;
        return users.findByName(username.trim());
    }

    @Tool("Get all orders for the given userId.")
    public List<OrderDto> get_orders_for_user(long userId) {
        return orders.getOrdersByUser(userId);
    }

    @Tool("Queue a cancellation for an order id (stub).")
    public Map<String, Object> cancel_order_stub(long orderId) {
        return Map.of("status", "queued", "orderId", orderId, "note", "stub");
    }

    @Tool("Queue a reorder for an order id (stub).")
    public Map<String, Object> reorder_stub(long orderId) {
        return Map.of("status", "queued", "orderId", orderId, "note", "stub");
    }


    @Tool("Search books by title/author/genre. 'limit' is max number of results (1-10).")
    public List<BookServiceClient.BookDto> search_books(String query, Integer limit) {
        int lim = (limit == null || limit < 1 || limit > 10) ? 5 : limit;
        return books.search(query == null ? "" : query.trim(), lim);
    }

    @Tool("Get personalized book title suggestions for a user. 'limit' is 1-10.")
    public List<String> suggest_books(long userId, Integer limit) {
        int lim = (limit == null || limit < 1 || limit > 10) ? 5 : limit;
        return books.suggestions(userId, lim);
    }

    @Tool("""
          Create a simple order for a user. Provide userId, bookId (if known, else null), and quantity (>=1).
          Returns the created order id or null if it failed.
          """)
    public Long create_order(long userId, Long bookId, int quantity) {
        OrderDto created = orders.createOrderSimple(userId, bookId, Math.max(1, quantity));
        return (created != null) ? created.getId() : null;
    }
}
