package com.intellicart.aiassistantservice.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class BookServiceClient {

    private static final String BOOK_SVC = "http://localhost:8080";
    private final RestTemplate http = new RestTemplate();

    public record BookDto(String id, String title, String author) {}

    public List<BookDto> search(String query, int limit) {
        try {
            String url = BOOK_SVC + "/api/books/search?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&limit=" + limit;
            BookDto[] arr = http.getForObject(url, BookDto[].class);
            return (arr != null) ? Arrays.asList(arr) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<String> suggestions(Long userId, int limit) {
        try {
            String url = BOOK_SVC + "/api/books/suggestions?userId=" + userId + "&limit=" + limit;
            String[] arr = http.getForObject(url, String[].class);
            return (arr != null) ? Arrays.asList(arr) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
