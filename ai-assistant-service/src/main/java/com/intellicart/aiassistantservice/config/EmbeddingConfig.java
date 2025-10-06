package com.intellicart.aiassistantservice.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
// Full precision:
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
// Quantized option (commented):
// import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${embeddings.minilm.quantized:false}") boolean useQuantized
    ) {
        if (useQuantized) {
            throw new IllegalStateException(
                    "Enable quantized by uncommenting the Quantized import + return line."
            );
        }
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
