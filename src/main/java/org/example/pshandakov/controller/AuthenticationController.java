package org.example.pshandakov.controller;

import lombok.RequiredArgsConstructor;
import org.example.pshandakov.model.ActionAuthRegHistory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.example.pshandakov.configuration.JwtTokenProvider;
import org.example.pshandakov.model.ApplicationUser;
import org.example.pshandakov.model.AuthenticationRequest;
import org.example.pshandakov.model.AuthenticationResponse;
import org.example.pshandakov.repository.ApplicationUserRepository;
import org.example.pshandakov.repository.ActionAuthRegHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final ApplicationUserRepository applicationUserRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final ActionAuthRegHistoryRepository actionAuthRegHistoryRepository;
    private final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthenticationRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();


        if (!StringUtils.hasText(email)) {
            return ResponseEntity.badRequest().body("Поле email не должно быть пустым.");
        }
        if (!StringUtils.hasText(password)) {
            return ResponseEntity.badRequest().body("Поле password не должно быть пустым.");
        }

        try {
            ApplicationUser user = applicationUserRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Пользователь с таким email не найден."));

            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));

            String token = jwtTokenProvider.createToken(email, user.getRole().getGrantedAuthorities());
            String username = user.getUsername();
            String message = "Добро пожаловать, " + username + "!";
            AuthenticationResponse response = new AuthenticationResponse(email, token, message);


            ActionAuthRegHistory historyEntry = new ActionAuthRegHistory();
            historyEntry.setEmail(email);
            historyEntry.setUsername(username);
            historyEntry.setTimeAction(LocalDateTime.now());
            historyEntry.setDescription("Успешный вход");
            historyEntry.setActionType("Авторизация");
            actionAuthRegHistoryRepository.save(historyEntry);


            return ResponseEntity.ok(response);
        } catch (UsernameNotFoundException ex) {
            logFailedLogin(email, "Пользователь с таким email не найден.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (BadCredentialsException ex) {
            logFailedLogin(email, "Неверный пароль.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный пароль.");
        } catch (AuthenticationException ex) {
            logFailedLogin(email, "Ошибка аутентификации: " + ex.getMessage());
            logger.error("Ошибка аутентификации: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации.");
        }
    }

    private void logFailedLogin(String email, String reason) {
        ActionAuthRegHistory historyEntry = new ActionAuthRegHistory();
        historyEntry.setEmail(email);
        historyEntry.setTimeAction(LocalDateTime.now());
        historyEntry.setActionType("Авторизация");
        historyEntry.setDescription("Неудачная попытка входа: email=" + email + ", причина=" + reason);
        actionAuthRegHistoryRepository.save(historyEntry);
        logger.warn("Неудачная попытка входа: email={}, reason={}", email, reason);
    }
}


