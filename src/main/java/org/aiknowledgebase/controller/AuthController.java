package org.aiknowledgebase.controller;

import org.aiknowledgebase.dto.CurrentUserResponse;
import org.aiknowledgebase.dto.LoginRequest;
import org.aiknowledgebase.dto.LoginResponse;
import org.aiknowledgebase.dto.RegisterRequest;
import org.aiknowledgebase.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public CurrentUserResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return authService.getCurrentUser(authorizationHeader);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
        return Map.of("message", "已退出登录");
    }
}