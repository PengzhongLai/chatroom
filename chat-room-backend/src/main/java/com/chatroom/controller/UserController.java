package com.chatroom.controller;

import com.chatroom.dto.ApiResponse;
import com.chatroom.dto.request.ThemeUpdateRequest;
import com.chatroom.dto.request.UserStatusRequest;
import com.chatroom.dto.response.CurrentUserResponse;
import com.chatroom.entity.User;
import com.chatroom.mapper.UserResponseMapper;
import com.chatroom.service.PresenceService;
import com.chatroom.service.UserService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PresenceService presenceService;
    private final UserResponseMapper userResponseMapper;

    public UserController(
            UserService userService,
            PresenceService presenceService,
            UserResponseMapper userResponseMapper
    ) {
        this.userService = userService;
        this.presenceService = presenceService;
        this.userResponseMapper = userResponseMapper;
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        User user = userService.getCurrentUser();
        return ApiResponse.success(userResponseMapper.toCurrentUser(user));
    }

    @PutMapping("/status")
    public ApiResponse<Void> setStatus(@Valid @RequestBody UserStatusRequest request) {
        User user = userService.getCurrentUser();
        presenceService.setStatus(user.getId(), request.status());
        return ApiResponse.success(null);
    }

    @PutMapping("/theme")
    public ApiResponse<Void> setTheme(@Valid @RequestBody ThemeUpdateRequest request) {
        User user = userService.getCurrentUser();
        user.setTheme(request.theme());
        userService.save(user);
        return ApiResponse.success(null);
    }

    @GetMapping("/presence")
    public ApiResponse<Map<Long, String>> getPresence() {
        return ApiResponse.success(presenceService.getAllStatuses());
    }
}
