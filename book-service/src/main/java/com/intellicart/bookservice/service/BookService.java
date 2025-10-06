package com.intellicart.bookservice.service;

import com.intellicart.bookservice.domain.Book;
import com.intellicart.bookservice.infrastructure.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BookService {

    private final BookRepository repo;

    public BookService(BookRepository repo) {
        this.repo = repo;
    }

    public List<Book> all() {
        return repo.findAll();
    }

    public Optional<Book> get(Long id) {
        return repo.findById(id);
    }

    public Optional<Book> getByExactTitle(String title) {
        return repo.findByTitleIgnoreCase(title);
    }

    public List<Book> searchByTitle(String q) {
        return repo.findByTitleContainingIgnoreCase(q);
    }

    @Transactional
    public Book create(Book book) {
        return repo.save(book);
    }

    @Transactional
    public Optional<Book> update(Long id, Book updated) {
        return repo.findById(id).map(b -> {
            b.setTitle(updated.getTitle());
            b.setAuthor(updated.getAuthor());
            b.setDescription(updated.getDescription());
            b.setGenre(updated.getGenre());
            b.setPrice(updated.getPrice());
            b.setAverageRating(updated.getAverageRating());
            return repo.save(b);
        });
    }

    @Transactional
    public Optional<Book> updatePartial(Long id, Map<String, Object> patch) {
        return repo.findById(id).map(b -> {
            if (patch.containsKey("title")) {
                Object v = patch.get("title");
                b.setTitle(v != null ? v.toString() : null);
            }
            if (patch.containsKey("author")) {
                Object v = patch.get("author");
                b.setAuthor(v != null ? v.toString() : null);
            }
            if (patch.containsKey("description")) {
                Object v = patch.get("description");
                b.setDescription(v != null ? v.toString() : null);
            }
            if (patch.containsKey("genre")) {
                Object v = patch.get("genre");
                b.setGenre(v != null ? v.toString() : null);
            }
            if (patch.containsKey("price")) {
                b.setPrice(asDouble(patch.get("price")));
            }
            if (patch.containsKey("averageRating")) {
                b.setAverageRating(asDouble(patch.get("averageRating")));
            }
            return repo.save(b);
        });
    }

    @Transactional
    public boolean delete(Long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    private static Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.valueOf(o.toString()); } catch (Exception e) { return null; }
    }
}
