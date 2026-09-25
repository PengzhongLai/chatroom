package com.chatroom.entity;

import com.chatroom.enums.UserStatus;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})  // 防止序列化 Hibernate 懒加载代理的内部字段
@Table(name = "users")
public class User {

    // password 存的是 BCrypt 摘要（60 字符，$2a$10$ 开头），不是明文，也无法解密。
    // @JsonIgnore 只是最后一道兜底：对外一律走 UserSummaryResponse（不含该字段）。
    //
    // createdAt 在 Java 侧赋当前时间（字段初始值），不是数据库 DEFAULT，
    // 所以应用服务器与数据库服务器时钟不同步时会有偏差。

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    @Column(length = 50)
    private String nickname;

    @Column(length = 255)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ONLINE;

    @Column(length = 10, nullable = false)
    private String theme = "dark";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {}

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
