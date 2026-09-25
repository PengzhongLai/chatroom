package com.chatroom.entity;

import com.chatroom.enums.UserStatus;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * 一个用户账号。对应 users 表。
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "users")
public class User {

    /** 用户 ID，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名，全局唯一 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** 密码的 BCrypt 摘要（60 字符），不是明文，也无法反解 */
    @Column(nullable = false)
    @JsonIgnore
    private String password;

    /** 昵称，最长 50 字符；注册时不填则等于用户名 */
    @Column(length = 50)
    private String nickname;

    /** 头像地址 */
    @Column(length = 255)
    private String avatarUrl;

    /** 持久化的用户状态，默认在线 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ONLINE;

    /** 前端主题，取值 dark 或 light */
    @Column(length = 10, nullable = false)
    private String theme = "dark";

    /** 注册时间，写入后不再修改 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** JPA 反射创建实体时使用 */
    public User() {}

    /** 用登录名、密码摘要和昵称创建用户；昵称为空时回退为用户名 */
    public User(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname != null ? nickname : username;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
