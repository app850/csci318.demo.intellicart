package com.intellicart.userservice.service;

import com.intellicart.userservice.domain.User;
import com.intellicart.userservice.infrastructure.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    public List<User> findAll() {
        return repo.findAll();
    }

    public User findOne(Long id) {
        return repo.findById(id).orElse(null);
    }

    public User create(User user) {
        user.setId(null);
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDate.now());
        }
        return repo.save(user);
    }

    public boolean delete(Long id) {
        if (!repo.existsById(id)) {
            return false;
        }
        repo.deleteById(id);
        return true;
    }

    public User findByUsername(String username) {
        return repo.findByUsernameIgnoreCase(username).orElse(null);
    }
}
