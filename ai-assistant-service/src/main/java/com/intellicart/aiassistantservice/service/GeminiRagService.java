package com.intellicart.aiassistantservice.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GeminiRagService {

    private static final Logger log = LoggerFactory.getLogger(GeminiRagService.class);

    private static final int TOP_K = 12;
    private static final double MIN_SCORE = 0.22;
    private static final int MAX_SNIPPETS_PER_SOURCE = 3;
    private static final int MAX_SOURCES_IN_PROMPT = 10;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final RagAssistant ragAssistant;

    public GeminiRagService(EmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> store,
                            RagAssistant ragAssistant) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.ragAssistant = ragAssistant;
    }

    public String answer(String question) {
        try {
            if (!StringUtils.hasText(question)) return "Please ask a non-empty question.";
            if (embeddingModel == null || store == null || ragAssistant == null) {
                return "Search isn’t available right now. Try again shortly.";
            }

            Embedding q = embeddingModel.embed(question).content();
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(q)
                    .maxResults(TOP_K)
                    .minScore(MIN_SCORE)
                    .build();
            EmbeddingSearchResult<TextSegment> result = store.search(req);
            List<EmbeddingMatch<TextSegment>> matches = result == null ? List.of() : result.matches();
            if (matches.isEmpty()) {
                return "I couldn’t find support for that in the catalogue index. Try rephrasing or broadening the topic.";
            }

            Map<String, List<String>> bySource = groupBySource(matches);
            if (bySource.isEmpty()) {
                return "I couldn’t find support for that in the catalogue index. Try rephrasing or broadening the topic.";
            }

            String sourcesList = bySource.keySet().stream().limit(8).collect(Collectors.joining(", "));
            String ctx = buildContextBlock(bySource);

            String prompt = """
                    You are answering a user using ONLY the CONTEXT below (snippets from indexed books).
                    If the answer is not supported by the CONTEXT, say you can't find it in the index and suggest a short alternative query.

                    • Be concise and conversational (3–6 sentences).
                    • If numbers or titles appear, be precise.
                    • Do not fabricate details outside the CONTEXT.

                    USER QUESTION:
                    %s

                    CONTEXT:
                    %s

                    Write a short helpful answer based strictly on the CONTEXT.
                    """.formatted(question.trim(), ctx);

            String reply = safeCall(prompt);
            reply = reply == null || reply.isBlank()
                    ? "I ran into an issue preparing an answer. Please try again."
                    : reply.trim();

            if (!sourcesList.isBlank()) {
                reply = reply + "\n\nSources: " + sourcesList;
            }
            return reply;

        } catch (Exception e) {
            log.error("RAG error: {}", e.getMessage(), e);
            return "RAG error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
    public List<String> recommendFromCatalogue(String preference, int max) {
        try {
            if (!StringUtils.hasText(preference)) {
                return List.of("Please tell me a preference (e.g., “cozy fantasy heists” or “space opera politics”).");
            }
            if (embeddingModel == null || store == null || ragAssistant == null) {
                return List.of("Recommendations aren’t available right now. Try again shortly.");
            }

            Embedding q = embeddingModel.embed(preference).content();
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(q)
                    .maxResults(Math.max(30, max * 10))
                    .minScore(Math.min(0.18, MIN_SCORE))
                    .build();
            EmbeddingSearchResult<TextSegment> result = store.search(req);
            List<EmbeddingMatch<TextSegment>> matches = result == null ? List.of() : result.matches();
            if (matches.isEmpty()) {
                return List.of("No strong matches in the catalogue index. Try a broader or different vibe.");
            }

            Map<String, List<String>> bySource = groupBySource(matches);
            if (bySource.isEmpty()) {
                return List.of("No strong matches in the catalogue index. Try a broader or different vibe.");
            }

            String ctx = buildContextBlock(bySource, MAX_SOURCES_IN_PROMPT, MAX_SNIPPETS_PER_SOURCE);

            String sentinel = "No strong matches in the catalogue index. Try a broader or different vibe.";
            // --- PROMPT CHANGE: ask for plain lines (no bullets/numbering) so we can number locally
            String prompt = """
                    Recommend up to %d books that match the USER PREFERENCE using ONLY the CANDIDATE CONTEXT below.
                    Output exactly one recommendation per line as:
                    Title — one-line reason
                    Do NOT add numbering or bullets. No extra commentary.
                    If nothing fits well, return exactly:
                    %s

                    USER PREFERENCE:
                    %s

                    CANDIDATE CONTEXT (grouped by [SOURCE: id]):
                    %s
                    """.formatted(max, sentinel, preference.trim(), ctx);

            String raw = safeCall(prompt);
            if (raw == null || raw.isBlank()) return List.of(sentinel);
            String trimmed = raw.trim();
            if (trimmed.equalsIgnoreCase(sentinel)) return List.of(sentinel);

            List<String> lines = Arrays.stream(trimmed.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> s.replaceFirst("^\\s*([\\-*•\\d]+[.)])\\s*", ""))
                    .collect(Collectors.toList());

            if (lines.isEmpty()) return List.of(sentinel);
            if (lines.size() > max) lines = lines.subList(0, max);

            List<String> cleaned = lines.stream()
                    .map(s -> s.replaceFirst("^\\s*([\\-*•\\d]+[.)])\\s*", "")) // remove leading bullets/numbers
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());
            if (cleaned.isEmpty()) return List.of(sentinel);

            StringBuilder sb = new StringBuilder();
            sb.append("Please choose a book (1–").append(cleaned.size()).append("):\n");
            for (int i = 0; i < cleaned.size(); i++) {
                sb.append(i + 1).append(". ").append(cleaned.get(i)).append("\n");
            }
            return List.of(sb.toString().trim());


        } catch (Exception e) {
            log.error("recommendFromCatalogue error: {}", e.getMessage(), e);
            return List.of("No strong matches right now. Try a broader or different vibe.");
        }
    }
    private static final Pattern SOURCE_TAG = Pattern.compile("^\\s*\\[\\s*source\\s*:\\s*([^\\]]+)\\]\\s*", Pattern.CASE_INSENSITIVE);

    private Map<String, List<String>> groupBySource(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream()
                .filter(m -> m != null && m.embedded() != null && m.embedded().text() != null)
                .sorted(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed())
                .collect(Collectors.toMap(
                        m -> {
                            String src = extractSource(m.embedded().text());
                            return src == null ? "<unknown>" : src;
                        },
                        m -> {
                            String s = sanitize(m.embedded().text());
                            return StringUtils.hasText(s) ? new ArrayList<>(List.of(s)) : new ArrayList<>();
                        },
                        (a, b) -> { a.addAll(b); return a; },
                        LinkedHashMap::new
                ));
    }

    private String buildContextBlock(Map<String, List<String>> bySource) {
        return buildContextBlock(bySource, MAX_SOURCES_IN_PROMPT, MAX_SNIPPETS_PER_SOURCE);
    }

    private String buildContextBlock(Map<String, List<String>> bySource, int maxSources, int maxSnippetsPerSource) {
        StringBuilder ctx = new StringBuilder();
        int sourceCount = 0;
        for (Map.Entry<String, List<String>> e : bySource.entrySet()) {
            if (sourceCount >= maxSources) break;
            String source = e.getKey();
            List<String> snippets = e.getValue();
            if (snippets == null || snippets.isEmpty()) continue;

            ctx.append("[SOURCE: ").append(source).append("]\n");
            int added = 0;
            for (String s : snippets) {
                if (!StringUtils.hasText(s)) continue;
                ctx.append("- ").append(s).append("\n");
                if (++added >= maxSnippetsPerSource) break;
            }
            ctx.append("\n");
            sourceCount++;
        }
        return ctx.toString().trim();
    }

    private String safeCall(String prompt) {
        try { return ragAssistant.chat(prompt); }
        catch (Exception e) {
            log.warn("ragAssistant.chat failed: {}", e.getMessage());
            return null;
        }
    }

    private static String extractSource(String text) {
        if (text == null) return null;
        Matcher m = SOURCE_TAG.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String sanitize(String text) {
        if (text == null) return "";
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > 900) oneLine = oneLine.substring(0, 900) + " …";
        return oneLine;
    }
}
