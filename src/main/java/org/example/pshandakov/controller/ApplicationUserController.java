package org.example.pshandakov.controller;

import org.example.pshandakov.model.ApplicationUser;
import org.example.pshandakov.repository.ApplicationUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class ApplicationUserController {

    private final ApplicationUserRepository userRepository;

    @Autowired
    public ApplicationUserController(ApplicationUserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @GetMapping
    public List<ApplicationUser> getAllUsers() {
        return userRepository.findAll();
    }


    @GetMapping("/{id}")
    public ResponseEntity<ApplicationUser> getUserById(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<ApplicationUser> user = userRepository.findById(id);
        return user.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }


    @PostMapping
    public ResponseEntity<ApplicationUser> createUser(@RequestBody ApplicationUser user) {
        if (user == null || user.getEmail() == null || user.getUsername() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(userRepository.save(user));
    }


    @PutMapping("/{id}")
    public ResponseEntity<ApplicationUser> updateUser(@PathVariable Long id, @RequestBody ApplicationUser user) {
        if (id == null || user == null || user.getEmail() == null || user.getUsername() == null) {
            return ResponseEntity.badRequest().build();
        }
        user.setId(id);
        return ResponseEntity.ok(userRepository.save(user));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

