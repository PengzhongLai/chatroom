package com.chatroom.controller;

import com.chatroom.dto.ApiResponse;
import com.chatroom.dto.LoginResponse;
import com.chatroom.dto.request.LoginRequest;
import com.chatroom.dto.request.RegisterRequest;
import com.chatroom.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request.username(), request.password());
        return ApiResponse.success(response);
    }
}
