package com.intellicart.bookservice.presentation;

import com.intellicart.bookservice.domain.Book;
import com.intellicart.bookservice.service.BookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService svc;

    public BookController(BookService svc) {
        this.svc = svc;
    }

    @GetMapping
    public List<Book> all() {
        return svc.all();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> get(@PathVariable Long id) {
        return svc.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/title/{title}")
    public ResponseEntity<Book> getByExactTitle(@PathVariable String title) {
        return svc.getByExactTitle(title).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public List<Book> search(@RequestParam(name = "q") String q) {
        return svc.searchByTitle(q);
    }

    @PostMapping
    public Book create(@RequestBody Book book) {
        return svc.create(book);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Book> update(@PathVariable Long id, @RequestBody Map<String, Object> patch) {
        return svc.updatePartial(id, patch).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return svc.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
