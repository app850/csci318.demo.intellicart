package com.intellicart.aiassistantservice.convo;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TurnStore {
    private final Map<String, TurnContext> sessions = new ConcurrentHashMap<>();

    public TurnContext load(String sessionId) {
        return sessions.computeIfAbsent(sessionId, TurnContext::new);
    }

    public void save(String sessionId, TurnContext ctx) {
        sessions.put(sessionId, ctx);
    }
}
