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

/**
 * 用户账号业务：注册、登录，以及从 SecurityContext 取当前登录用户。
 * 注册只写库不签发令牌；登录校验密码后才签发 JWT。
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    /** BCrypt 编码器：注册时 encode，登录时 matches */
    private final PasswordEncoder passwordEncoder;
    /** 登录成功后签发令牌 */
    private final JwtTokenProvider jwtTokenProvider;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** 注册账号。用户名先 trim 清洗再查重，密码只存 BCrypt 摘要；昵称缺省用用户名 */
    public void register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw BusinessException.conflict("用户名已存在");
        }
        // 单向哈希，无法反解；同一密码每次 encode 结果都不同（随机盐）
        String encodedPassword = passwordEncoder.encode(request.password());
        String nickname = request.nickname() != null && !request.nickname().isBlank()
                ? request.nickname().trim() : username;
        User user = new User(username, encodedPassword, nickname);
        userRepository.save(user);
    }

    /**
     * 登录。按用户名查用户，用 matches 比对密码，成功后签发 JWT。
     * 用户不存在与密码错误返回同一句提示，避免暴露哪些用户名已注册。
     */
    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        // 不能用 encode 后比字符串：随机盐导致每次结果不同，必须用 matches
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        LoginResponse.UserInfo info = new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl()
        );
        return new LoginResponse(token, info);
    }

    /**
     * 取当前登录用户。身份来自 JwtAuthenticationFilter 写入的 SecurityContextHolder，
     * principal 是 Long 类型的 userId；未登录或用户已删除时抛未授权异常。
     */
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

    /** 保存用户实体。供本类与其他服务透传使用 */
    public void save(User user) {
        userRepository.save(user);
    }
}
