package com.chatroom.entity;

import com.chatroom.enums.MessageType;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * 一条消息，同时用于频道消息和私聊消息。对应 messages 表。
 * 靠"哪个外键非空"区分两类消息：
 *   channel_id 非空、private_chat_id 为空 → 频道消息
 *   channel_id 为空、private_chat_id 非空 → 私聊消息
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "messages", indexes = {
    @Index(name = "idx_channel_created", columnList = "channel_id, created_at")
})
public class Message {

    /** 消息 ID，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属频道；私聊消息为 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    /** 所属私聊会话；频道消息为 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "private_chat_id")
    private PrivateChat privateChat;

    /** 发送者，两种消息都必填 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** 消息正文 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 消息类型，默认文本 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type = MessageType.TEXT;

    /** 附件的原始文件名；无附件为 null */
    private String fileName;

    /** 附件的存储路径（已用 UUID 重命名）；无附件为 null */
    @Column(length = 500)
    private String filePath;

    /** 是否已撤回。撤回只改这个标记，正文仍留在数据库里 */
    @Column(nullable = false)
    private Boolean isRecalled = false;

    /** 发送时间 */
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** JPA 反射创建实体时使用 */
    public Message() {}

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public PrivateChat getPrivateChat() { return privateChat; }
    public void setPrivateChat(PrivateChat privateChat) { this.privateChat = privateChat; }
    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Boolean getIsRecalled() { return isRecalled; }
    public void setIsRecalled(Boolean isRecalled) { this.isRecalled = isRecalled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
