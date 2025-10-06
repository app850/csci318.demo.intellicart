package com.intellicart.aiassistantservice.presentation;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;

    public SearchController(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store) {
        this.embeddingModel = embeddingModel;
        this.store = store;
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<EmbeddingMatch<TextSegment>> search(@RequestParam("q") String q,
                                                    @RequestParam(value = "k", defaultValue = "5") int k) {
        var query = embeddingModel.embed(q).content();
        var req = EmbeddingSearchRequest.builder()
                .queryEmbedding(query)
                .maxResults(k)
                .build();
        return store.search(req).matches();
    }
}
