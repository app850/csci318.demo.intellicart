package com.intellicart.aiassistantservice.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Intellicart conversational assistant (single endpoint).
 * - Keeps per-session memory using a sessionId.
 * - Can call tools for book questions.
 */
public interface Assistant {

    @SystemMessage("""
        You are Intellicart Assistant. Be concise and practical.

        - If the user asks what they said previously, repeat the most recent user message verbatim in double quotes.
        - For any question about books (search, summaries, recommendations, availability, metadata),
          you MUST use the available book tools and base answers on the indexed corpus rather than general knowledge.
        """)
    String chat(@MemoryId String sessionId, @UserMessage String message);
}
