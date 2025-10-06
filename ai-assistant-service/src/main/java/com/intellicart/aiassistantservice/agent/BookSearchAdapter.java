package com.intellicart.aiassistantservice.agent;

import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class BookSearchAdapter implements AssistantToolsImpl.BookSearch {

    private static final String BOOK_SVC = "http://localhost:8080";
    private final RestTemplate http = new RestTemplate();

    @Override
    public List<String> search(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of("Please provide a title, author, or genre to search.");
        }

        try {
            String url = BOOK_SVC + "/api/books/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
            ResponseEntity<List> resp = http.getForEntity(url, List.class);
            Object body = resp.getBody();

            List<String> out = new ArrayList<>();
            if (body instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?,?> m) {
                        Object title = m.get("title");
                        Object author = m.get("author");
                        if (title != null && author != null) {
                            out.add(title.toString() + " — " + author.toString());
                        } else if (title != null) {
                            out.add(title.toString());
                        }
                    }
                    if (out.size() >= 5) break;
                }
            }
            if (out.isEmpty()) {
                return List.of("No matches found in the catalogue. Try another title/author?");
            }
            return out;
        } catch (Exception e) {
            return List.of("Sorry—couldn’t reach the book catalogue right now.");
        }
    }
}
