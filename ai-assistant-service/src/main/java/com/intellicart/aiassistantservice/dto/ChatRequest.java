package com.intellicart.aiassistantservice.dto;

public record ChatRequest(
        String sessionId,
        String userName,
        String message
) {}
