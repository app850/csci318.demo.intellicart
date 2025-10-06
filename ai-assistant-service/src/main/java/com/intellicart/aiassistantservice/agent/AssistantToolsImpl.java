package com.intellicart.aiassistantservice.agent;

import com.intellicart.aiassistantservice.presentation.RequestContext;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class AssistantToolsImpl implements AssistantTools {

    @Value("${user.service.base-url:http://localhost:8081}")
    private String USER_SVC;

    @Value("${order.service.base-url:http://localhost:8082}")
    private String ORDER_SVC;

    @Value("${bookservice.base-url:http://localhost:8080}")
    private String BOOK_SVC;

    private final RestTemplate http;
    private final BookSearch bookSearch;
    private final RequestContext ctx;

    public AssistantToolsImpl(BookSearch bookSearch, RequestContext ctx) {
        this.bookSearch = bookSearch;
        this.ctx = ctx;

        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(10000);
        this.http = new RestTemplate(f);
    }

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_OF_OBJECTS =
            new ParameterizedTypeReference<>() {};

    @Override
    @Tool("List all users from user-service")
    public List<Map<String, Object>> list_users() {
        try {
            ResponseEntity<List<Map<String, Object>>> r = http.exchange(
                    USER_SVC + "/api/users",
                    HttpMethod.GET,
                    null,
                    LIST_OF_MAPS
            );
            return r.getBody() != null ? r.getBody() : List.of();
        } catch (Exception ex) {
            return List.of(Map.of(
                    "error", "user-service unreachable",
                    "detail", ex.getMessage()
            ));
        }
    }

    @Override
    @Tool("Get a user by id from user-service")
    public Map<String, Object> get_user_by_id(long user_id) {
        try {
            ResponseEntity<Map<String, Object>> r = http.exchange(
                    USER_SVC + "/api/users/" + user_id,
                    HttpMethod.GET,
                    null,
                    MAP_OF_OBJECTS
            );
            return r.getBody() != null ? r.getBody() : Map.of("message", "No user found");
        } catch (Exception ex) {
            return Map.of(
                    "error", "user-service unreachable",
                    "detail", ex.getMessage()
            );
        }
    }

    @Tool("Find a user by display name using client-side matching over /api/users. Returns {id,name,email} and sets current user in context if found.")
    public Map<String, Object> find_user_by_name(String name) {
        if (name == null || name.isBlank()) return Map.of();
        final String needle = name.trim().toLowerCase(Locale.ROOT);
        try {
            ResponseEntity<List<Map<String, Object>>> r = http.exchange(
                    USER_SVC + "/api/users",
                    HttpMethod.GET,
                    null,
                    LIST_OF_MAPS
            );
            List<Map<String, Object>> all = r.getBody() != null ? r.getBody() : List.of();
            if (all.isEmpty()) return Map.of();

            Map<String, Object> exact = null, starts = null, contains = null;
            for (Map<String, Object> u : all) {
                if (u == null) continue;
                String uname = safeGetStr(u, "name");
                if (uname == null || uname.isBlank()) continue;
                String cmp = uname.toLowerCase(Locale.ROOT);
                if (cmp.equals(needle)) { exact = u; break; }
                if (starts == null && cmp.startsWith(needle)) starts = u;
                if (contains == null && cmp.contains(needle)) contains = u;
            }
            Map<String, Object> pick = exact != null ? exact : (starts != null ? starts : contains);
            if (pick == null) return Map.of();

            Map<String, Object> out = new LinkedHashMap<>();
            Object id = pick.getOrDefault("id", pick.get("userId"));
            Long uid = parseLong(id);
            if (uid != null) {
                out.put("id", uid);
                ctx.setUserId(uid);
            }
            String uname = safeGetStr(pick, "name");
            if (uname != null) out.put("name", uname);
            String email = safeGetStr(pick, "email");
            if (email != null) out.put("email", email);

            return out;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @Tool("Return the current user id inferred for this session (if any)")
    public Map<String, Object> get_current_user() {
        Long uid = ctx.getUserId();
        return (uid == null) ? Map.of() : Map.of("userId", uid);
    }

    @Tool("Set the current user id for this session (use when the user declares an id in chat)")
    public Map<String, Object> set_current_user_id(long user_id) {
        ctx.setUserId(user_id);
        return Map.of("userId", user_id, "status", "ok");
    }

    @Override
    @Tool("Look up all orders for a given user id from order-service")
    public List<Map<String, Object>> lookup_orders(long user_id) {
        try {
            ResponseEntity<List<Map<String, Object>>> r = http.exchange(
                    ORDER_SVC + "/api/orders/user/" + user_id,
                    HttpMethod.GET,
                    null,
                    LIST_OF_MAPS
            );
            List<Map<String, Object>> body = r.getBody();
            return body != null ? body : List.of();
        } catch (Exception ex) {
            return List.of(Map.of(
                    "error", "order-service unreachable",
                    "detail", ex.getMessage()
            ));
        }
    }

    @Tool("Look up all orders for the current user inferred from context")
    public List<Map<String, Object>> lookup_my_orders() {
        Long uid = ctx.getUserId();
        if (uid == null) {
            return List.of(Map.of(
                    "error", "missing_user_id",
                    "hint", "Say your name (e.g., 'I am Alice') or your id (e.g., 'my id is 1')"
            ));
        }
        return lookup_orders(uid);
    }

    @Tool("Get the most recent order for the current user")
    public Map<String, Object> last_order_for_me() {
        Long uid = ctx.getUserId();
        if (uid == null) {
            return Map.of(
                    "error", "missing_user_id",
                    "hint", "Say your name or id first"
            );
        }
        List<Map<String, Object>> orders = lookup_orders(uid);
        if (orders.isEmpty()) return Map.of("message", "No orders found");
        orders.sort(Comparator.comparing(o -> safeGetStr(o, "createdAt"), Comparator.nullsFirst(String::compareTo)));
        return orders.get(orders.size() - 1);
    }

    @Tool("Place an order by POST /api/orders with {userId, bookId}")
    public Map<String, Object> place_order(long user_id, long book_id) {
        try {
            String url = ORDER_SVC + "/api/orders";
            Map<String, Object> req = new HashMap<>();
            req.put("userId", user_id);
            req.put("bookId", book_id);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, headers);

            ResponseEntity<Map<String, Object>> r = http.exchange(url, HttpMethod.POST, entity, MAP_OF_OBJECTS);
            Map<String, Object> body = r.getBody();
            if (r.getStatusCode().is2xxSuccessful() && body != null) {
                Object newId = body.getOrDefault("id", body.get("orderId"));
                return (newId != null)
                        ? Map.of("status", "ok", "orderId", newId)
                        : Map.of("status", "ok");
            }
            return Map.of("status", "error", "httpStatus", r.getStatusCode().value());
        } catch (Exception ex) {
            return Map.of("status", "error", "detail", ex.getMessage());
        }
    }

    @Override
    @Tool("Recommend up to 3 books for a given preference text from our internal catalogue only")
    public List<String> recommend_books(String preference) {
        try {
            List<String> recs = bookSearch.search(preference);
            String out = formatAsSingleNumberedBlock(recs, "Please choose a book");
            return List.of(normalizeNewlines(out));
        } catch (Exception ex) {
            return List.of("Recommendation service error: " + ex.getMessage());
        }
    }

    @Override
    @Tool("Search the catalogue (title/authors). Return up to 5 'Title — Author' lines.")
    public List<String> search_catalogue(String query) {
        final String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of("Please provide a non-empty search query.");
        }

        try {
            String url = BOOK_SVC + "/api/books/search?q=" + urlEncode(q);
            ResponseEntity<List<Map<String, Object>>> r = http.exchange(url, HttpMethod.GET, null, LIST_OF_MAPS);
            List<Map<String, Object>> payload = r.getBody();
            if (payload != null && !payload.isEmpty()) {
                List<String> lines = toTitleAuthorLines(payload, 5);
                if (!lines.isEmpty()) return lines;
            }
        } catch (Exception ignore) {}

        try {
            ResponseEntity<List<Map<String, Object>>> r = http.exchange(
                    BOOK_SVC + "/api/books",
                    HttpMethod.GET,
                    null,
                    LIST_OF_MAPS
            );
            List<Map<String, Object>> all = r.getBody();
            if (all == null || all.isEmpty()) {
                return List.of("No catalogue data available.");
            }
            String needle = q.toLowerCase(Locale.ROOT);
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> m : all) {
                if (m == null) continue;
                String title = safeGetStr(m, "title");
                String authors = safeGetStr(m, "authors");
                boolean match = (title != null && title.toLowerCase(Locale.ROOT).contains(needle))
                        || (authors != null && authors.toLowerCase(Locale.ROOT).contains(needle));
                if (match) filtered.add(m);
            }
            if (filtered.isEmpty()) return List.of("No matches found in the catalogue for: " + q);
            return toTitleAuthorLines(filtered, 5);
        } catch (Exception e) {
            return List.of("Catalogue search error: " + e.getMessage());
        }
    }

    @Tool("Search books and return raw book objects (id/title/authors) to support ordering flows")
    public List<Map<String, Object>> search_books_raw(String query) {
        final String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return List.of();

        try {
            String url = BOOK_SVC + "/api/books/search?q=" + urlEncode(q);
            ResponseEntity<List<Map<String, Object>>> r = http.exchange(url, HttpMethod.GET, null, LIST_OF_MAPS);
            if (r.getBody() != null) return r.getBody();
        } catch (Exception ignore) {}

        try {
            ResponseEntity<List<Map<String, Object>>> r = http.exchange(
                    BOOK_SVC + "/api/books", HttpMethod.GET, null, LIST_OF_MAPS);
            List<Map<String, Object>> all = r.getBody();
            if (all == null) return List.of();
            String needle = q.toLowerCase(Locale.ROOT);
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> m : all) {
                if (m == null) continue;
                String title = safeGetStr(m, "title");
                String authors = safeGetStr(m, "authors");
                boolean match = (title != null && title.toLowerCase(Locale.ROOT).contains(needle))
                        || (authors != null && authors.toLowerCase(Locale.ROOT).contains(needle));
                if (match) filtered.add(m);
            }
            return filtered;
        } catch (Exception e) {
            return List.of();
        }
    }
    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static List<String> toTitleAuthorLines(List<Map<String, Object>> items, int limit) {
        List<String> out = new ArrayList<>();
        int count = 0;
        for (Map<String, Object> m : items) {
            if (m == null) continue;
            String title = safeGetStr(m, "title");
            String authors = safeGetStr(m, "authors");
            if (title == null || title.isBlank()) continue;

            String line = (authors != null && !authors.isBlank())
                    ? title.trim() + " — " + authors.trim()
                    : title.trim();

            out.add(line);
            if (++count >= limit) break;
        }
        return out;
    }

    private static String safeGetStr(Map<String, Object> m, String key) {
        if (m == null || key == null) return null;
        Object v = m.get(key);
        return (v == null) ? null : String.valueOf(v);
    }

    private static Long parseLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); }
        catch (Exception e) { return null; }
    }

    public interface BookSearch {
        List<String> search(String query);
    }
    private static String normalizeNewlines(String s) {
        if (s == null) return "";
        // 1) normalize platform endings to \n
        String t = s.replace("\r\n", "\n").replace("\r", "\n");
        // 2) if upstream sent literal backslash-n, turn it into a real newline
        t = t.replace("\\n", "\n");
        // 3) trim only; DO NOT do any regex magic on plain 'n'
        return t.trim();
    }

    private static String formatAsSingleNumberedBlock(List<String> lines, String heading) {
        if (lines == null || lines.isEmpty()) {
            return "No recommendations at the moment.";
        }
        if (lines.size() == 1) {
            return normalizeNewlines(lines.get(0));
        }

        List<String> flat = new ArrayList<>();
        for (String s : lines) {
            if (s == null) continue;
            String norm = normalizeNewlines(s);
            String[] parts = norm.split("\\r?\\n");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isBlank()) {
                    trimmed = trimmed.replaceFirst("^\\s*([\\-*•\\d]+[.)])\\s*", "");
                    flat.add(trimmed);
                }
            }
        }
        if (flat.isEmpty()) return "No recommendations at the moment.";

        StringBuilder sb = new StringBuilder();
        sb.append(heading != null && !heading.isBlank() ? heading : "Please choose a book");
        sb.append(" (1–").append(flat.size()).append("):\n");
        for (int i = 0; i < flat.size(); i++) {
            sb.append(i + 1).append(". ").append(flat.get(i)).append("\n");
        }
        return normalizeNewlines(sb.toString());
    }
}
