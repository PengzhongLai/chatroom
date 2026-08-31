package com.chatroom.controller;

import com.chatroom.dto.ApiResponse;
import com.chatroom.dto.request.UserSearchQuery;
import com.chatroom.dto.response.UserSummaryResponse;
import com.chatroom.entity.User;
import com.chatroom.mapper.UserResponseMapper;
import com.chatroom.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import jakarta.validation.Valid;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserSearchController {

    private final UserRepository userRepository;
    private final UserResponseMapper userResponseMapper;

    public UserSearchController(UserRepository userRepository, UserResponseMapper userResponseMapper) {
        this.userRepository = userRepository;
        this.userResponseMapper = userResponseMapper;
    }

    @GetMapping("/search")
    public ApiResponse<List<UserSummaryResponse>> search(
            @Valid @ModelAttribute UserSearchQuery query) {
        String keyword = query.getQ().trim();
        List<User> users = userRepository.findByUsernameContainingIgnoreCaseOrNicknameContainingIgnoreCase(
                keyword, keyword, PageRequest.of(0, 10)
        );
        List<UserSummaryResponse> results = users.stream()
            .map(userResponseMapper::toSummary)
            .toList();
        return ApiResponse.success(results);
    }
}
