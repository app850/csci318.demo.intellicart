package com.intellicart.aiassistantservice.service;

import dev.langchain4j.service.UserMessage;

public interface RagAssistant {
    String chat(@UserMessage String message);
}
