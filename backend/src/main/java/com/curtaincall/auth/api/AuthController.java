package com.curtaincall.auth.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.curtaincall.auth.application.AccessToken;
import com.curtaincall.auth.application.LoginService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    /** spec 6.1. 오류 순서: 400 INVALID_INPUT(검증) → 401 INVALID_CREDENTIALS. */
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AccessToken token = loginService.login(request.username(), request.password());
        return new LoginResponse(token.value(), token.expiresInSeconds());
    }
}
