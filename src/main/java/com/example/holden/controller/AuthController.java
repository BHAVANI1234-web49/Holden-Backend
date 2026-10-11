package com.example.holden.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.holden.entity.User;
import com.example.holden.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"https://holdenproject.netlify.app", "http://localhost:3000", "http://localhost:5173"})
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            User registeredUser = authService.register(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Registration successful. You can now log in.");
            response.put("user", Map.of(
                    "id", registeredUser.getId(),
                    "username", registeredUser.getUsername(),
                    "fullName", registeredUser.getFullName() != null ? registeredUser.getFullName() : registeredUser.getUsername(),
                    "email", registeredUser.getEmail() != null ? registeredUser.getEmail() : "",
                    "role", registeredUser.getRole()
            ));

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "An unexpected error occurred during registration."
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        try {
            String uname = username;
            String pwd = password;

            if (body != null) {
                if (body.containsKey("username")) {
                    uname = body.get("username");
                }
                if (body.containsKey("password")) {
                    pwd = body.get("password");
                }
            }

            User user = authService.login(uname, pwd);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Login successful.");
            response.put("user", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "fullName", user.getFullName() != null ? user.getFullName() : user.getUsername(),
                    "email", user.getEmail() != null ? user.getEmail() : "",
                    "role", user.getRole()
            ));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "An error occurred during login."
            ));
        }
    }
}
