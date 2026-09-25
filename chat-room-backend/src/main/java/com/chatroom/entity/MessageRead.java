package com.chatroom.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "message_reads", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"message_id", "user_id"})
})
public class MessageRead {

    // 一行 = 某人读过某条消息，靠 (message_id, user_id) 唯一约束保证不重复。
    // 注意粒度：前端进入频道时只上报"最后一条消息 ID"，不会把之前的每条消息
    // 都写一条记录，所以这张表不能用来断言"该用户已读过全部历史消息"。

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime readAt = LocalDateTime.now();

    public MessageRead() {}

    public MessageRead(Message message, User user) {
        this.message = message;
        this.user = user;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Message getMessage() { return message; }
    public void setMessage(Message message) { this.message = message; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
