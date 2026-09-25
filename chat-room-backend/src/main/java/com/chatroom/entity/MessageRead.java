package com.chatroom.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * 一条已读回执，表示"某个用户读过某条消息"。对应 message_reads 表。
 * (message_id, user_id) 组合唯一。
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "message_reads", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"message_id", "user_id"})
})
public class MessageRead {

    /** 已读记录 ID，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被读的消息 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    /** 读这条消息的用户 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 读取时间 */
    @Column(nullable = false)
    private LocalDateTime readAt = LocalDateTime.now();

    /** JPA 反射创建实体时使用 */
    public MessageRead() {}

    /** 记录某个用户读了某条消息 */
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
