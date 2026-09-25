package com.chatroom.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * 一个聊天频道。对应 channels 表。
 * 创建者记录在 creator_id，它同时是该频道 CREATOR 成员记录的依据。
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "channels")
public class Channel {

    /** 频道 ID，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 频道名称，最长 100 字符，不可为空 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 频道描述，最长 255 字符，可为空 */
    @Column(length = 255)
    private String description;

    /** 创建者。判断频道所有权时比较它的 id，不要看成员表的角色 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    /** 是否公开频道，默认公开 */
    @Column(nullable = false)
    private Boolean isPublic = true;

    /** 邀请码，仅私密频道生成，全局唯一 */
    @Column(length = 20, unique = true)
    private String inviteCode;

    /** 是否全员禁言，默认否 */
    @Column(nullable = false)
    private Boolean isMuted = false;

    /** 创建时间，写入后不再修改 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** JPA 反射创建实体时使用 */
    public Channel() {}

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }
    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public Boolean getIsMuted() { return isMuted; }
    public void setIsMuted(Boolean isMuted) { this.isMuted = isMuted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
