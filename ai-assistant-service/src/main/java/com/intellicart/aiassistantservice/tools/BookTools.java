package com.intellicart.aiassistantservice.tools;

import com.intellicart.aiassistantservice.convo.ToolResult;
import com.intellicart.aiassistantservice.convo.TurnContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component("bookTool")
public class BookTools implements Tool {

    private static final String BOOK_SVC = "http://localhost:8080";
    private final RestTemplate http = new RestTemplate();

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult handle(String action, Map<String, Object> args, TurnContext ctx) {
        try {
            switch (action) {
                case "search" -> {
                    String q = text(args.get("query"));
                    if (!StringUtils.hasText(q)) return ToolResult.err("query is required");
                    Object res = getJson(BOOK_SVC + "/api/books/search?q=" + url(q));
                    return ToolResult.ok(Map.of("items", res));
                }
                case "recommend" -> {
                    String pref = text(args.get("preference"));
                    Object res = recommendCatalogue(pref);
                    return ToolResult.ok(Map.of("items", res));
                }
                default -> { return ToolResult.err("unsupported book action: " + action); }
            }
        } catch (Exception e) {
            return ToolResult.err(e.getMessage());
        }
    }

    private Object recommendCatalogue(String pref) {
        String p = (pref == null ? "" : pref.toLowerCase(Locale.ROOT)).trim();
        if (p.isEmpty()) {
            return getJson(BOOK_SVC + "/api/books"); // fall back: first page
        }
        if (p.contains("sci") || p.contains("science")) {
            Object byGenre = getJson(BOOK_SVC + "/api/books?genre=sci-fi");
            if (nonEmptyList(byGenre)) return limit(byGenre, 3);
        }
        if (p.contains("fantasy")) {
            Object byGenre = getJson(BOOK_SVC + "/api/books?genre=fantasy");
            if (nonEmptyList(byGenre)) return limit(byGenre, 3);
        }
        if (p.contains("romance")) {
            Object byGenre = getJson(BOOK_SVC + "/api/books?genre=romance");
            if (nonEmptyList(byGenre)) return limit(byGenre, 3);
        }
        Object bySearch = getJson(BOOK_SVC + "/api/books/search?q=" + url(pref));
        if (nonEmptyList(bySearch)) return limit(bySearch, 3);
        return getJson(BOOK_SVC + "/api/books");
    }

    private boolean nonEmptyList(Object o) { return (o instanceof List<?> l) && !l.isEmpty(); }

    private Object limit(Object o, int n) {
        if (o instanceof List<?> l) return l.subList(0, Math.min(n, l.size()));
        return o;
    }

    private Object getJson(String url) {
        ResponseEntity<Object> resp = http.getForEntity(url, Object.class);
        return resp.getBody();
    }

    private String url(String s) { return s == null ? "" : s.replace(" ", "%20"); }
    private String text(Object v) { return v == null ? null : String.valueOf(v); }
}
