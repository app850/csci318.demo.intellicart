package com.intellicart.aiassistantservice.agent;

import dev.langchain4j.agent.tool.Tool;

import java.util.List;
import java.util.Map;

public interface AssistantTools {

    @Tool("List all users and return the real results from user-service")
    List<Map<String, Object>> list_users();

    @Tool("Get a user by their id and return the real result from user-service")
    Map<String, Object> get_user_by_id(long user_id);

    @Tool("Look up all orders for a user id and return the real results from order-service")
    List<Map<String, Object>> lookup_orders(long user_id);

    @Tool("Recommend up to 3 books for a given preference text from our internal catalogue only")
    List<String> recommend_books(String preference);

    @Tool("Search the catalogue by free-text (matches title or author). Return up to 5 'Title — Author' lines.")
    List<String> search_catalogue(String query);
}
