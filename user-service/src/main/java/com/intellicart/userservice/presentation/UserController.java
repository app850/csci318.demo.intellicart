package com.intellicart.userservice.presentation;

import com.intellicart.userservice.domain.User;
import com.intellicart.userservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }


    @GetMapping
    public List<User> list() {
        return service.findAll();
    }


    @GetMapping("/{id}")
    public ResponseEntity<User> get(@PathVariable Long id) {
        User u = service.findOne(id);
        return (u == null) ? ResponseEntity.notFound().build() : ResponseEntity.ok(u);
    }


    @GetMapping("/by-name")
    public ResponseEntity<User> getByName(@RequestParam("q") String name) {
        User u = service.findByUsername(name);
        return (u == null) ? ResponseEntity.notFound().build() : ResponseEntity.ok(u);
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User body) {
        User saved = service.create(body);
        return ResponseEntity
                .created(URI.create("/api/users/" + saved.getId()))
                .body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = service.delete(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
