package com.intellicart.aiassistantservice.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookIndexService {

    private final RestTemplate rest;
    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel embeddingModel;

    public BookIndexService(EmbeddingStore<TextSegment> store,
                            EmbeddingModel embeddingModel) {
        this.rest = new RestTemplate();
        this.store = store;
        this.embeddingModel = embeddingModel;
    }

    /** Result DTO that UnifiedAssistantService expects */
    public static class Result {
        private final double similarity;
        private final String book;

        public Result(double similarity, String book) {
            this.similarity = similarity;
            this.book = book;
        }

        public double similarity() { return similarity; }
        public String book() { return book; }
    }

    /**
     * Call book-service (/api/books) and reindex all books.
     */
    public String reindex() {
        // Assuming book-service exposes GET http://localhost:8080/api/books
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> books =
                rest.getForObject("http://localhost:8080/api/books", List.class);

        if (books == null || books.isEmpty()) {
            return "Indexed 0 books (book-service returned nothing)";
        }

        int docs = 0, segs = 0;
        for (Map<String,Object> b : books) {
            String text = buildBookText(b);
            TextSegment seg = TextSegment.from("[source:book-service#" + b.get("id") + "] " + text);
            Embedding emb = embeddingModel.embed(seg.text()).content();
            store.add(emb, seg);

            docs++;
            segs++;
        }
        return "Indexed " + docs + " books (" + segs + " segments)";
    }

    /**
     * Semantic search against the embedded store.
     */
    public List<Result> searchTop(String query, int k) {
        Embedding q = embeddingModel.embed(query).content();
        EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                .queryEmbedding(q)
                .maxResults(k)
                .minScore(0.25)
                .build();

        EmbeddingSearchResult<TextSegment> res = store.search(req);
        if (res.matches() == null) return List.of();

        return res.matches().stream()
                .map(m -> new Result(m.score(), m.embedded().text()))
                .collect(Collectors.toList());
    }

    // --- helpers ---

    private static String buildBookText(Map<String,Object> b) {
        String title   = safe(b.get("title"));
        String author  = safe(b.get("author"));
        String genre   = safe(b.get("genre"));
        String summary = safe(b.get("summary"));
        String notes   = safe(b.get("notes"));

        return String.format("%s by %s. Genre: %s. %s %s",
                title, author, genre, summary, notes).trim();
    }

    private static String safe(Object o) {
        return (o == null) ? "" : String.valueOf(o).trim();
    }
}
