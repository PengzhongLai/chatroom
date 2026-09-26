package com.chatroom.controller;

import com.chatroom.dto.ApiResponse;
import com.chatroom.dto.LoginResponse;
import com.chatroom.dto.request.LoginRequest;
import com.chatroom.dto.request.RegisterRequest;
import com.chatroom.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


/**
 * 认证接口：注册与登录。
 * 这两个接口在 SecurityConfig 里是 permitAll —— 否则用户没 token 就登录不了，
 * 形成死锁。注意注册**不签发 token**，注册完仍需自行登录。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** 注册账号。只创建用户，不返回 token；用户名重复时返回冲突错误 */
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ApiResponse.success(null);
    }

    /** 登录。校验用户名密码后签发 JWT，返回 token 和用户摘要（不含密码） */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request.username(), request.password());
        return ApiResponse.success(response);
    }
}
