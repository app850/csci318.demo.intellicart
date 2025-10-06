package com.intellicart.aiassistantservice.config;

import com.intellicart.aiassistantservice.service.RagAssistant;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${google.ai.apiKey:}") String apiKey,
            @Value("${gemini.model:gemini-1.5-flash}") String modelName
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Missing google.ai.apiKey (set env GOOGLE_API_KEY or property google.ai.apiKey)"
            );
        }

        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
    @Bean
    public RagAssistant ragAssistant(ChatLanguageModel chatLanguageModel) {
        return AiServices.builder(RagAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }
}
