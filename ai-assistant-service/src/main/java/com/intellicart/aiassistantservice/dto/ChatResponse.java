package com.intellicart.aiassistantservice.dto;

public record ChatResponse(String answer, double confidence, String sessionId) {

    public ChatResponse(String answer) {
        this(answer, 1.0, "default"); // default confidence=1.0, sessionId=default
    }

    public ChatResponse(String answer, String sessionId) {
        this(answer, 1.0, sessionId);
    }
}
