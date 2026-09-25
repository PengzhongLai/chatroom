package com.chatroom.entity;

import com.chatroom.enums.ChatStatus;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * 两个人的一段私聊关系。对应 private_chats 表。
 * (user1_id, user2_id) 组合唯一，且是有序的：写入时按用户 ID 排序决定谁是 user1。
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "private_chats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user1_id", "user2_id"})
})
public class PrivateChat {

    /** 私聊会话 ID，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 参与者一，取两个用户中 ID 较小的那个 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    /** 参与者二，取两个用户中 ID 较大的那个 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    /** 发起申请的人，用于判断 PENDING 时谁是申请方、谁是接收方 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id", nullable = false)
    private User initiator;

    /** 会话状态，默认直接激活 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatStatus status = ChatStatus.ACTIVE;

    /** 创建时间，写入后不再修改 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** JPA 反射创建实体时使用 */
    public PrivateChat() {}

    /** 指定参与双方与发起者创建一段私聊关系 */
    public PrivateChat(User user1, User user2, User initiator) {
        this.user1 = user1;
        this.user2 = user2;
        this.initiator = initiator;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser1() { return user1; }
    public void setUser1(User user1) { this.user1 = user1; }
    public User getUser2() { return user2; }
    public void setUser2(User user2) { this.user2 = user2; }
    public User getInitiator() { return initiator; }
    public void setInitiator(User initiator) { this.initiator = initiator; }
    public ChatStatus getStatus() { return status; }
    public void setStatus(ChatStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
