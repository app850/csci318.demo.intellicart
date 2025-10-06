package com.intellicart.aiassistantservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@SuppressWarnings({"unchecked"})
public class UnifiedAssistantService {

    private static final String USER_SVC = "http://localhost:8081";
    private static final String ORDER_SVC = "http://localhost:8082";

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();
    private final GeminiRagService rag;

    public UnifiedAssistantService(GeminiRagService rag) { this.rag = rag; }

    private enum Stage { ASK_USERNAME, ASK_PASSWORD, ASK_BOOK_PREF, SHOW_RECS }

    private static class Rec { String title; long bookId; double price; String reason; }

    private static class Session {
        Stage stage = Stage.ASK_USERNAME;
        String username;
        String password;
        String bookPref;
        Long userId;
        List<Rec> recs = new ArrayList<>();
        List<Rec> cart = new ArrayList<>();
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Map<String, Object> handle(String message, boolean forceRag, String sessionId) {
        if (!StringUtils.hasText(message)) return out("Please provide a non-empty message.", 0.0, sessionId);

        Session s = sessions.computeIfAbsent(sessionId, k -> new Session());
        String m = message.trim();
        String lower = m.toLowerCase(Locale.ROOT);

        if (s.stage != Stage.SHOW_RECS) return handleWizard(s, m, sessionId);

        try {
            String lx = normalize(lower);

            if ((containsAny(lx, "overview", "summary") && lx.contains("user"))
                    || (lx.contains("user") && containsAny(lx, "order", "orders") && containsAny(lx, "book", "books", "recs", "recommend"))) {
                return userOverview(lx, sessionId);
            }
            if (matches(lx, "list all users", "show all users", "get all users")) {
                Object users = getJson(USER_SVC + "/api/users");
                return out("Users:\n" + pretty(users), 1.0, sessionId);
            }
            Long userId = extractFirstLong(lx, "(?:get|show)\\s+user\\s+(\\d+)");
            if (userId != null) {
                Object user = getJson(USER_SVC + "/api/users/" + userId);
                return out("User " + userId + ":\n" + pretty(user), 1.0, sessionId);
            }
            if (matches(lx, "list all orders", "show all orders", "get all orders")) {
                Object orders = getJson(ORDER_SVC + "/api/orders");
                return out("Orders:\n" + pretty(orders), 1.0, sessionId);
            }
            Long ordersFor = extractFirstLong(lx, "(?:show|list|get)\\s+orders\\s+for\\s+user\\s+(\\d+)");
            if (ordersFor != null) {
                Object orders = getJson(ORDER_SVC + "/api/orders/user/" + ordersFor);
                return out("Orders for user " + ordersFor + ":\n" + pretty(orders), 1.0, sessionId);
            }
            if (containsAny(lx, "start")) {
                s.stage = Stage.ASK_USERNAME; s.recs.clear(); s.cart.clear();
                return out("Welcome! Please enter your username.", 1.0, sessionId);
            }

            Map<String, Object> intent = interpretWithLLM(s, m);
            String action = String.valueOf(intent.getOrDefault("action", "unknown")).toLowerCase(Locale.ROOT);

            if (isNegative(lx) && ("unknown".equals(action) || "reject".equals(action))) {
                s.cart.clear();
                return out("No problem. I’ve cleared your cart. Tell me another vibe (e.g., “recommend fantasy heists”) or say “start”.", 1.0, sessionId);
            }

            if ("new_recs".equals(action)) {
                String pref = extractPref(lx, s.bookPref);
                if (StringUtils.hasText(pref)) s.bookPref = pref;
                s.recs = generateRecs(s.bookPref);
                return out(renderRecs(s.recs) + footerCart(s), 1.0, sessionId);
            }

            if ("add_to_cart".equals(action)) {
                List<Object> items = asList(intent.get("items"));
                boolean added = addItemsByIndicesOrTitles(s, items);
                if (!added) {
                    List<Integer> picks = parseIndices(lx, s.recs.size());
                    for (int idx : picks) {
                        Rec r = s.recs.get(idx - 1);
                        if (!s.cart.contains(r)) s.cart.add(r);
                        added = true;
                    }
                }
                if (!added) return out("Tell me which ones you want (e.g., “1 and 2”, or the titles).", 1.0, sessionId);
                return out(renderCart(s.cart) + "\nType “checkout” to place the order, or add more numbers.", 1.0, sessionId);
            }

            if ("remove_from_cart".equals(action)) {
                List<Object> items = asList(intent.get("items"));
                removeItemsByIndicesOrTitles(s, items);
                return out(renderCart(s.cart) + "\nAdd more or “checkout”.", 1.0, sessionId);
            }

            if ("checkout".equals(action)) {
                if (s.cart.isEmpty()) return out("Your cart is empty. Add items first (e.g., “1 and 2”).", 1.0, sessionId);
                Map<String, Object> order = placeOrder(s.userId, s.cart);
                String id = val(order, "id"); String total = val(order, "totalAmount");
                s.cart.clear();
                return out("Order placed ✅\nOrder ID: " + id + "\nAmount: " + total + "\nNeed anything else?", 1.0, sessionId);
            }

            if ("compare".equals(action) || looksCompare(lx)) {
                Rec best = pickBestRec(s, m);
                if (best == null) return out("They’re all strong picks. Want me to pick one and add it to your cart?", 1.0, sessionId);
                String text = "I’d go with **" + best.title + "** — " + best.reason + " (" + String.format("$%.2f", best.price) + ").\nAdd it to your cart?";
                return out(text, 1.0, sessionId);
            }

            if ("reject".equals(action)) {
                s.cart.clear();
                return out("Okay. Nothing added. Want me to suggest different books?", 1.0, sessionId);
            }

            if ("help".equals(action)) {
                return out("You can say things like: “which is best?”, “show more sci-fi”, “add 1 and 3”, “remove 2”, “checkout”, or “no thanks”.", 1.0, sessionId);
            }

            if ("unknown".equals(action)) {
                if (looksCompare(lx)) {
                    Rec best = pickBestRec(s, m);
                    if (best == null) return out("They’re all good. Want me to pick one and add it for you?", 1.0, sessionId);
                    String text = "I’d pick **" + best.title + "** — " + best.reason + ". Add it?";
                    return out(text, 1.0, sessionId);
                }
                if (looksLikeStandaloneGenre(m)) {
                    String pref = canonicalizeGenre(m);
                    s.bookPref = pref;
                    s.recs = generateRecs(s.bookPref);
                    return out(renderRecs(s.recs) + footerCart(s), 1.0, sessionId);
                }
                if (containsAny(lx, "checkout", "place order", "buy now", "confirm order")) {
                    if (s.cart.isEmpty()) return out("Your cart is empty. Add items first (e.g., “1 and 2”).", 1.0, sessionId);
                    Map<String, Object> order = placeOrder(s.userId, s.cart);
                    String id = val(order, "id"); String total = val(order, "totalAmount");
                    s.cart.clear();
                    return out("Order placed ✅\nOrder ID: " + id + "\nAmount: " + total + "\nNeed anything else?", 1.0, sessionId);
                }
                List<Integer> picks = parseIndices(lx, s.recs.size());
                if (!picks.isEmpty()) {
                    for (int idx : picks) {
                        Rec r = s.recs.get(idx - 1);
                        if (!s.cart.contains(r)) s.cart.add(r);
                    }
                    return out(renderCart(s.cart) + "\nType “checkout” to place the order, or add more numbers.", 1.0, sessionId);
                }
                boolean looksBooky = containsAny(lx, "book", "novel", "recommend", "sci-fi", "scifi", "science fiction", "fantasy", "author");
                if (forceRag || looksBooky) {
                    s.bookPref = extractPref(lx, s.bookPref);
                    if (!StringUtils.hasText(s.bookPref)) s.bookPref = "popular sci-fi";
                    s.recs = generateRecs(s.bookPref);
                    return out(renderRecs(s.recs) + footerCart(s), 1.0, sessionId);
                }
                return out("""
                        I didn’t catch that.
                        Try: “which is best?”, “1 and 2”, “checkout”, “recommend fantasy heists”, “no thanks”, or “start”.
                        """, 0.8, sessionId);
            }

            return out("Okay.", 1.0, sessionId);

        } catch (Exception ex) {
            return out("Something went wrong: " + ex.getMessage(), 0.0, sessionId);
        }
    }

    private Map<String, Object> handleWizard(Session s, String m, String sessionId) {
        if (s.stage == Stage.ASK_USERNAME && looksStartLike(m)) return out("Welcome! Please enter your username.", 1.0, sessionId);

        switch (s.stage) {
            case ASK_USERNAME -> {
                if (looksStartLike(m)) return out("Welcome! Please enter your username.", 1.0, sessionId);
                s.username = m;
                Long found = findUserIdByUsername(s.username);
                if (found == null) return out("I couldn't find that username. Try again or type 'start' to restart.", 0.7, sessionId);
                s.userId = found;
                s.stage = Stage.ASK_PASSWORD;
                return out("Thanks, " + s.username + ". Enter your password.", 1.0, sessionId);
            }
            case ASK_PASSWORD -> {
                s.password = m;
                s.stage = Stage.ASK_BOOK_PREF;
                return out("Great. What kind of books are you in the mood for today?", 1.0, sessionId);
            }
            case ASK_BOOK_PREF -> {
                s.bookPref = m;
                s.recs = generateRecs(s.bookPref);
                s.cart.clear();
                s.stage = Stage.SHOW_RECS;
                return out(renderRecs(s.recs) + footerCart(s), 1.0, sessionId);
            }
            default -> { return out("Say 'start' to begin.", 1.0, sessionId); }
        }
    }

    private Map<String, Object> userOverview(String lower, String sessionId) {
        Long id = extractFirstLong(lower, "user\\s+(\\d+)");
        Map<String, Object> user = null;
        if (id != null) { try { user = (Map<String, Object>) getJson(USER_SVC + "/api/users/" + id); } catch (Exception ignored) {} }
        if (id == null || user == null) return out("I couldn’t resolve the user. Try “summary for user 1”.", 0.7, sessionId);

        Object orders = "[]";
        try { orders = getJson(ORDER_SVC + "/api/orders/user/" + id); } catch (Exception ignored) {}

        String pref = "popular sci-fi";
        Session maybe = sessions.get(sessionId);
        if (maybe != null && Objects.equals(maybe.userId, id) && StringUtils.hasText(maybe.bookPref)) pref = maybe.bookPref;

        String recsText;
        try {
            String prompt = ("Recommend 3 books for a reader who likes: %s. Keep it brief: title – one-line reason.").formatted(pref);
            recsText = rag.answer(prompt);
        } catch (Exception e) {
            recsText = fallbackRecText(pref);
        }

        String text = ("User: %s (id=%s)\n\nOrders:\n%s\n\nRecommendations:\n%s")
                .formatted(String.valueOf(user.getOrDefault("username", "unknown")), id, pretty(orders), recsText);
        return out(text.trim(), 1.0, sessionId);
    }

    private Map<String, Object> interpretWithLLM(Session s, String userMessage) {
        String prompt = """
            You are a strict intent parser. Output ONLY compact JSON:
            {"action":"add_to_cart|remove_from_cart|checkout|reject|new_recs|compare|help|unknown","items":[numberOrTitleOrPref...]}

            - “which is best?”, “what should I pick?”, “your top pick?” -> action "compare"
            - If user wants different books, action "new_recs" with a short preference phrase in items (single string).
            - If user declines, "reject".
            - For adding/removing, items can be numbers (1..N) or titles.
            - If unsure, "unknown".

            User: "%s"
            Recs (1..N): %s
            Cart: %s
            """.formatted(userMessage, renderRecsShort(s.recs), renderCartShort(s.cart));
        try {
            String raw = rag.answer(prompt);
            if (raw == null) return Map.of("action","unknown");
            String t = raw.trim();
            if (t.startsWith("```")) t = t.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```\\s*$", "").trim();
            return json.readValue(t, Map.class);
        } catch (Exception e) {
            return Map.of("action","unknown");
        }
    }

    private Rec pickBestRec(Session s, String userMessage) {
        if (s.recs.isEmpty()) return null;
        String prompt = """
            Choose the single best option for the user from this list by title only. Consider broad appeal and the message intent.
            Respond with ONLY the exact title, no extra words.

            User said: %s

            Options:
            %s
            """.formatted(userMessage, renderRecsShort(s.recs));
        try {
            String ans = rag.answer(prompt);
            if (ans == null) return s.recs.get(0);
            String t = ans.trim().replaceAll("^[-•\\d.\\)\\s]+", "");
            Rec match = matchTitle(t, s.recs);
            return match != null ? match : s.recs.get(0);
        } catch (Exception ignored) {
            return s.recs.get(0);
        }
    }

    private boolean addItemsByIndicesOrTitles(Session s, List<Object> items) {
        boolean added = false;
        if (items == null) return false;
        for (Object it : items) {
            if (it == null) continue;
            Rec r = null;
            if (it instanceof Number n) {
                int idx = n.intValue();
                if (idx >= 1 && idx <= s.recs.size()) r = s.recs.get(idx - 1);
            } else {
                String t = String.valueOf(it).trim().toLowerCase(Locale.ROOT);
                r = matchTitle(t, s.recs);
            }
            if (r != null && !s.cart.contains(r)) { s.cart.add(r); added = true; }
        }
        return added;
    }

    private void removeItemsByIndicesOrTitles(Session s, List<Object> items) {
        if (items == null) return;
        for (Object it : items) {
            Rec r = null;
            if (it instanceof Number n) {
                int idx = n.intValue();
                if (idx >= 1 && idx <= s.recs.size()) r = s.recs.get(idx - 1);
            } else {
                String t = String.valueOf(it).trim().toLowerCase(Locale.ROOT);
                r = matchTitle(t, s.cart.isEmpty() ? s.recs : s.cart);
            }
            if (r != null) s.cart.remove(r);
        }
    }

    private Rec matchTitle(String q, List<Rec> list) {
        if (!StringUtils.hasText(q)) return null;
        String x = q.toLowerCase(Locale.ROOT);
        for (Rec r : list) if (r.title != null && r.title.toLowerCase(Locale.ROOT).contains(x)) return r;
        return null;
    }

    private List<Object> asList(Object v) {
        if (v == null) return Collections.emptyList();
        if (v instanceof List<?> l) return new ArrayList<>(l);
        return Collections.singletonList(v);
    }

    private List<Rec> generateRecs(String pref) {
        try {
            String prompt = ("Recommend 3 books for a reader who likes: %s. " +
                    "Format lines as: Title — one-line reason. No extras.")
                    .formatted(pref);
            String ans = rag.answer(prompt);
            if (ans != null) {
                String t = ans.trim();
                if (t.startsWith("{") || t.startsWith("[") || t.toLowerCase(Locale.ROOT).contains("\"error\"")) {
                    return fallbackRecs(pref);
                }
            }
            List<Rec> r = parseRecText(ans);
            if (r.isEmpty()) r = fallbackRecs(pref);
            return r;
        } catch (Exception ignored) {
            return fallbackRecs(pref);
        }
    }

    private List<Rec> parseRecText(String text) {
        List<Rec> out = new ArrayList<>();
        if (text == null) return out;
        String[] lines = text.split("\\r?\\n");
        long baseId = 7000; double price = 14.99;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("{") || t.startsWith("[") || t.startsWith("\"") || t.contains(":")) continue;
            String[] parts = t.split("\\s+—\\s+|\\s+-\\s+|\\s+–\\s+", 2);
            String title = parts[0].replaceAll("^[-•\\d.\\)\\s]+", "").trim();
            if (!StringUtils.hasText(title)) continue;
            String reason = parts.length > 1 ? parts[1].trim() : "Good fit for your taste.";
            Rec r = new Rec(); r.title = title; r.reason = reason; r.bookId = baseId++; r.price = price; price += 2.00;
            out.add(r);
            if (out.size() == 3) break;
        }
        return out;
    }

    private List<Rec> fallbackRecs(String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        List<Rec> r = new ArrayList<>();
        if (containsAny(p, "sci", "science", "space", "dune")) {
            r.add(rec("Dune", 7101, 19.99, "Epic politics and prophecy."));
            r.add(rec("Foundation", 7102, 17.49, "Civilization, math, fate."));
            r.add(rec("The Three-Body Problem", 7103, 18.25, "First contact, high stakes."));
            return r;
        }
        if (containsAny(p, "fantasy", "wizard", "magic")) {
            r.add(rec("The Name of the Wind", 7201, 16.99, "A gifted student of arcane arts."));
            r.add(rec("Mistborn", 7202, 15.49, "Heist fantasy with unique magic."));
            r.add(rec("The Lies of Locke Lamora", 7203, 18.99, "Clever rogues, rich worldbuilding."));
            return r;
        }
        r.add(rec("Project Hail Mary", 7301, 19.49, "Science-forward, high-stakes adventure."));
        r.add(rec("The Martian", 7302, 14.99, "Witty survival on Mars."));
        r.add(rec("Red Rising", 7303, 17.99, "Uprising in a stratified future."));
        return r;
    }

    private String fallbackRecText(String pref) { return renderRecs(fallbackRecs(pref)); }

    private Rec rec(String title, long id, double price, String reason) { Rec r = new Rec(); r.title = title; r.bookId = id; r.price = price; r.reason = reason; return r; }

    private String renderRecs(List<Rec> recs) {
        StringBuilder sb = new StringBuilder("Here are some picks:\n");
        for (int i = 0; i < recs.size(); i++) {
            Rec r = recs.get(i);
            sb.append(i + 1).append(". ").append(r.title).append(" — ").append(r.reason)
                    .append(" (").append(String.format("$%.2f", r.price)).append(")\n");
        }
        sb.append("\nAdd to cart by saying numbers (e.g., \"1 and 2\" or titles), or say “checkout”.");
        return sb.toString().trim();
    }

    private String renderRecsShort(List<Rec> recs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recs.size(); i++) {
            Rec r = recs.get(i);
            sb.append(i + 1).append(") ").append(r.title);
            if (i < recs.size() - 1) sb.append("; ");
        }
        return sb.toString();
    }

    private String renderCart(List<Rec> cart) {
        if (cart.isEmpty()) return "Cart is empty.";
        double total = 0.0;
        StringBuilder sb = new StringBuilder("In your cart:\n");
        for (Rec r : cart) { total += r.price; sb.append("- ").append(r.title).append(" (").append(String.format("$%.2f", r.price)).append(")\n"); }
        sb.append("Total: ").append(String.format("$%.2f", total));
        return sb.toString().trim();
    }

    private String renderCartShort(List<Rec> cart) {
        if (cart.isEmpty()) return "(empty)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cart.size(); i++) {
            Rec r = cart.get(i);
            sb.append(r.title);
            if (i < cart.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private Map<String, Object> placeOrder(Long userId, List<Rec> items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        List<Map<String, Object>> its = new ArrayList<>();
        double total = 0.0;
        for (Rec r : items) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("bookId", r.bookId);
            it.put("quantity", 1);
            it.put("price", r.price);
            its.add(it);
            total += r.price;
        }
        payload.put("items", its);
        payload.put("totalAmount", total);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<Object> resp = http.postForEntity(ORDER_SVC + "/api/orders", new HttpEntity<>(payload, headers), Object.class);
            Object body = resp.getBody();
            if (body instanceof Map<?, ?> m) return (Map<String, Object>) m;
        } catch (Exception ignored) {}
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", "unknown");
        out.put("totalAmount", total);
        return out;
    }

    private Long findUserIdByUsername(String username) {
        try {
            Object raw = getJson(USER_SVC + "/api/users");
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Object uname = m.get("username");
                        if (uname != null && uname.toString().equalsIgnoreCase(username)) {
                            Object id = m.get("id");
                            if (id != null) return Long.valueOf(id.toString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean looksCompare(String x) {
        return containsAny(x, "which is best", "which one is best", "which is better", "pick one", "top pick", "what should i pick", "best one", "favourite", "favorite");
    }

    private boolean matches(String input, String... options) { for (String o : options) if (input.equals(o)) return true; return false; }
    private boolean containsAny(String input, String... needles) { for (String n : needles) if (input.contains(n)) return true; return false; }
    private boolean looksStartLike(String m) { String x = m.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""); return x.contains("start") || x.startsWith("st") || x.equals("s") || x.equals("go"); }

    private List<Integer> parseIndices(String lower, int max) {
        String x = lower.replaceAll("[,;&]", " ").replaceAll("\\band\\b", " ").replaceAll("\\s+", " ");
        List<Integer> picks = new ArrayList<>();
        Matcher mm = Pattern.compile("(?:^|\\s)(?:n)?(\\d{1,2})(?=\\s|$)").matcher(x);
        while (mm.find()) {
            try {
                int v = Integer.parseInt(mm.group(1));
                if (v >= 1 && v <= max && !picks.contains(v)) picks.add(v);
            } catch (NumberFormatException ignored) {}
        }
        return picks;
    }

    private String extractPref(String lower, String current) {
        Matcher m1 = Pattern.compile("(?:recommend|suggest|like|in the mood for|mood for|interested in|prefer)\\s+([^.;:]+)").matcher(lower);
        if (m1.find()) {
            String cand = m1.group(1).trim();
            cand = cand.replaceAll("\\b(books|book|novels|novel|please)\\b", "").trim();
            if (StringUtils.hasText(cand)) return cand;
        }
        if (lower.contains("sci-fi") || lower.contains("scifi") || lower.contains("science fiction")) return "sci-fi";
        if (lower.contains("fantasy") || lower.contains("wizard") || lower.contains("magic")) return "fantasy";
        return StringUtils.hasText(current) ? current : null;
    }

    private String footerCart(Session s) { return ("\n\n" + renderCart(s.cart) + "\nAdd items by number or title, or type “checkout”.").trim(); }

    private String normalize(String s) {
        String x = s.toLowerCase(Locale.ROOT);
        x = x.replaceAll("\\bbut\\s*(n\\d+)\\b", "buy $1");
        x = x.replaceAll("\\bcan i but\\b", "can i buy");
        x = x.replaceAll("\\bi want to but\\b", "i want to buy");
        x = x.replaceAll("\\badd\\s*(n\\d+)\\b", "$1");
        return x;
    }

    private boolean isNegative(String x) {
        return containsAny(x, "no thank you","no thanks","nope","nah","not now","cancel","skip","leave it","stop","none","not interested");
    }

    private Long extractFirstLong(String input, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(input);
        if (m.find()) { try { return Long.parseLong(m.group(1)); } catch (NumberFormatException ignored) {} }
        return null;
    }

    private Object getJson(String url) { ResponseEntity<Object> resp = http.getForEntity(url, Object.class); return resp.getBody(); }

    private String pretty(Object obj) {
        if (obj == null) return "null";
        try { return json.writerWithDefaultPrettyPrinter().writeValueAsString(obj); }
        catch (JsonProcessingException e) { return String.valueOf(obj); }
    }

    private String val(Map<String, Object> map, String key) { Object v = map == null ? null : map.get(key); return v == null ? "unknown" : String.valueOf(v); }

    private Map<String, Object> out(String answer, double confidence, String sessionId) {
        return Map.of("answer", answer, "confidence", confidence, "sessionId", sessionId);
    }
    // put near other helpers
    private static final Set<String> CANON_GENRES = new HashSet<>(Arrays.asList(
            "sci fi","scifi","science fiction","fantasy","romance","mystery","thriller",
            "crime","horror","historical","nonfiction","non-fiction","ya","young adult",
            "literary","adventure","space opera","dystopian","urban fantasy","magical realism"
    ));

    private String canonicalizeGenre(String s) {
        String x = s.toLowerCase(Locale.ROOT).trim();
        x = x.replaceAll("\\bsci[-\\s]*fi\\b", "sci fi");
        x = x.replaceAll("\\bnon\\s*fiction\\b", "nonfiction");
        x = x.replaceAll("\\byoung\\s*adult\\b", "ya");
        return x;
    }

    private boolean looksLikeStandaloneGenre(String m) {
        String x = canonicalizeGenre(m);
        if (CANON_GENRES.contains(x)) return true;
        // also allow short “<genre> + qualifier” like "sci fi epics", "romance comedies"
        if (x.split("\\s+").length <= 3) {
            for (String g : CANON_GENRES) if (x.startsWith(g)) return true;
        }
        return false;
    }
}
