package org.example.pshandakov.controller;

import lombok.RequiredArgsConstructor;
import org.example.pshandakov.model.*;
import org.example.pshandakov.repository.ActionAuthRegHistoryRepository;
import org.example.pshandakov.repository.ApplicationUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class RegistrationController {

    private final ApplicationUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActionAuthRegHistoryRepository actionAuthRegHistoryRepository;
    private final Logger logger = LoggerFactory.getLogger(RegistrationController.class);
    private static final int MIN_PASSWORD_LENGTH = 6;



    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegistrationRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            logRegistrationAttempt(request.getEmail(), null, "Ошибка регистрации: Email уже используется");
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ошибка регистрации: Email уже используется");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            logRegistrationAttempt(request.getEmail(), request.getUsername(), "Ошибка регистрации: Username уже используется");
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ошибка регистрации: Username уже используется");
        }

        String passwordFeedback = checkPasswordComplexity(request.getPassword());
        if (passwordFeedback != null) {
            logRegistrationAttempt(request.getEmail(), request.getUsername(), passwordFeedback);
            return ResponseEntity.badRequest().body(passwordFeedback);
        }

        try {
            ApplicationUser newUser = new ApplicationUser();
            newUser.setUsername(request.getUsername());
            newUser.setEmail(request.getEmail());
            newUser.setPassword(passwordEncoder.encode(request.getPassword()));
            newUser.setRole(ApplicationRole.USER);

            userRepository.save(newUser);
            logRegistrationAttempt(request.getEmail(), request.getUsername(), "Успешная регистрация");
            logger.info("Пользователь {} успешно зарегистрировался.", request.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body("Пользователь успешно зарегистрирован");
        } catch (Exception ex) {
            logRegistrationAttempt(request.getEmail(), request.getUsername(), "Ошибка регистрации: " + ex.getMessage());
            logger.error("Ошибка при регистрации пользователя: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка регистрации");
        }
    }

    private void logRegistrationAttempt(String email, String username, String description) {
        ActionAuthRegHistory historyEntry = new ActionAuthRegHistory();
        historyEntry.setEmail(email);
        historyEntry.setUsername(username);
        historyEntry.setTimeAction(LocalDateTime.now());
        historyEntry.setDescription(description);
        historyEntry.setActionType("Регистрация");
        actionAuthRegHistoryRepository.save(historyEntry);
    }
    private String checkPasswordComplexity(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Ошибка регистрации: Пароль слишком короткий. Требуется не менее " + MIN_PASSWORD_LENGTH + " символов.";
        }

        boolean hasUpperCase = !password.equals(password.toLowerCase());
        boolean hasLowerCase = !password.equals(password.toUpperCase());
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecialChar = password.matches(".*[!@#\\$%\\^&\\*].*");


        if (!hasUpperCase && !hasLowerCase) return "Ошибка регистрации: Слабый пароль, используйте символы разных регистров.";
        if (!hasDigit && !hasSpecialChar) return "Ошибка регистрации: Слабый пароль, используйте цифры или специальные символы (!@#$%^&*)";
        return null;
    }
}
