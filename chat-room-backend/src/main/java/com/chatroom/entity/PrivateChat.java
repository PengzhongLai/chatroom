package com.chatroom.entity;

import com.chatroom.enums.ChatStatus;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "private_chats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user1_id", "user2_id"})
})
public class PrivateChat {

    // 唯一约束是"有序"的：数据库认为 (1,2) 与 (2,1) 是两组不同的值。
    // 所以同一段私聊只存一行，靠的是 PrivateChatService 在写入前按用户 ID 排序
    // 决定谁是 user1、谁是 user2；排序一旦漏做，就会出现两条互为镜像的记录。
    //
    // initiator_id 记录"谁先发起的申请"，用于判断 PENDING 状态下当前用户是
    // 申请方还是接收方，从而决定接受/拒绝/反向申请的分支。
    // 状态流转与消息清理都在 PrivateChatService，本实体只保存状态。

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id", nullable = false)
    private User initiator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatStatus status = ChatStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PrivateChat() {}

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
