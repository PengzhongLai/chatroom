package com.chatroom.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "channels")
public class Channel {

    // 所有权权威：判断"谁是创建者"一律比较 creator_id（即 creator.getId()），
    // 不要看 channel_members.role。V2__repair_channel_ownership.sql 的存在
    // 就是因为历史版本只改镜像角色、没改这一列。
    //
    // name 上没有唯一约束（本库只有 invite_code 和 users.username 是 UNIQUE），
    // 重名只靠 ChannelService.createChannel 的 existsByName 先查后写来防，
    // 并发下存在两个同名频道同时通过检查的窗口。
    //
    // creator 是 LAZY：getCreator() 返回代理，只读 getId() 不会发 SQL，
    // 读 getNickname() 之类的非主键字段才会触发一次 SELECT users。

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false)
    private Boolean isPublic = true;

    @Column(length = 20, unique = true)
    private String inviteCode;

    @Column(nullable = false)
    private Boolean isMuted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

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
