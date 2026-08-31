package com.chatroom.service;

import com.chatroom.dto.LoginResponse;
import com.chatroom.dto.request.RegisterRequest;
import com.chatroom.entity.User;
import com.chatroom.exception.BusinessException;
import com.chatroom.repository.UserRepository;
import com.chatroom.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public void register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw BusinessException.conflict("用户名已存在");
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        String nickname = request.nickname() != null && !request.nickname().isBlank()
                ? request.nickname().trim() : username;
        User user = new User(username, encodedPassword, nickname);
        userRepository.save(user);
    }

    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        LoginResponse.UserInfo info = new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl()
        );
        return new LoginResponse(token, info);
    }

    public User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Long userId)) {
            throw BusinessException.unauthorized("用户未登录");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.unauthorized("登录用户不存在"));
    }

    public void save(User user) {
        userRepository.save(user);
    }
}
