package com.intellicart.aiassistantservice.tools;

import com.intellicart.aiassistantservice.convo.ToolResult;
import com.intellicart.aiassistantservice.convo.TurnContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Component("userTool")
public class UserTools implements Tool {

    private static final String USER_SVC = "http://localhost:8081";
    private final RestTemplate http = new RestTemplate();

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult handle(String action, Map<String, Object> args, TurnContext ctx) {
        try {
            switch (action) {
                case "listUsers" -> {
                    Object users = getJson(USER_SVC + "/api/users");
                    return ToolResult.ok(Map.of("users", users));
                }
                case "getUser" -> {
                    Long id = asLong(args.get("userId"));
                    if (id == null) return ToolResult.err("userId is required");
                    Object user = getJson(USER_SVC + "/api/users/" + id);
                    if (user instanceof Map<?, ?> u && u.get("id") != null) {
                        ctx.setUserId(asLong(u.get("id")));
                    }
                    return ToolResult.ok(Map.of("user", user));
                }
                case "resolveUserByName" -> {
                    String username = asText(args.get("username"));
                    if (!StringUtils.hasText(username)) return ToolResult.err("username is required");
                    Object raw = getJson(USER_SVC + "/api/users");
                    if (raw instanceof List<?> list) {
                        for (Object o : list) {
                            if (o instanceof Map<?, ?> m) {
                                Object uname = m.get("username");
                                if (uname != null && uname.toString().equalsIgnoreCase(username)) {
                                    Long id = asLong(m.get("id"));
                                    if (id != null) ctx.setUserId(id);
                                    return ToolResult.ok(Map.of("user", m));
                                }
                            }
                        }
                    }
                    return ToolResult.err("username not found");
                }
                default -> { return ToolResult.err("unsupported user action: " + action); }
            }
        } catch (Exception e) {
            return ToolResult.err(e.getMessage());
        }
    }

    private Object getJson(String url) {
        ResponseEntity<Object> resp = http.getForEntity(url, Object.class);
        return resp.getBody();
    }

    private Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception ignored) { return null; }
    }

    private String asText(Object v) { return v == null ? null : String.valueOf(v); }
}
