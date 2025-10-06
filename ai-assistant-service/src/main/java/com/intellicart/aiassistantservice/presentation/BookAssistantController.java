package com.intellicart.aiassistantservice.presentation;

import com.intellicart.aiassistantservice.service.BookIndexService;
import com.intellicart.aiassistantservice.service.GeminiRagService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assistant/books")
public class BookAssistantController {

    private final BookIndexService index;
    private final GeminiRagService rag;

    public BookAssistantController(BookIndexService index,
                                   GeminiRagService rag) {
        this.index = index;
        this.rag = rag;
    }

    @PostMapping("/reindex")
    @ResponseStatus(HttpStatus.OK)
    public String reindex() {
        return index.reindex();
    }

    public static class SearchHit {
        public double similarity;
        public String text;
        public SearchHit(double similarity, String text) {
            this.similarity = similarity;
            this.text = text;
        }
    }

    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    public List<SearchHit> search(
            @RequestParam("q") String query,
            @RequestParam(value = "k", defaultValue = "5") int k
    ) {
        return index.searchTop(query, k).stream()
                .map(r -> new SearchHit(r.similarity(), r.book()))
                .toList();
    }

    @GetMapping("/qa")
    @ResponseStatus(HttpStatus.OK)
    public String qa(@RequestParam("q") String question) {
        return rag.answer(question);
    }
}
