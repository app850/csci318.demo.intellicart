package com.intellicart.aiassistantservice.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    @Bean(name = "llmChatModel")
    @Primary
    public ChatLanguageModel llmChatModel(
            @Value("${google.ai.apiKey:${gemini.api-key:${GOOGLE_AI_API_KEY:}}}") String apiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String modelName
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key not set (google.ai.apiKey / gemini.api-key / GOOGLE_AI_API_KEY).");
        }
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.1)
                .maxOutputTokens(512)
                .build();
    }
}
