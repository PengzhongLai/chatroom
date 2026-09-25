package com.chatroom.entity;

import com.chatroom.enums.HistoryLevel;
import com.chatroom.enums.MemberRole;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * 一条成员记录，表示"某个用户在某个频道里的成员身份"。对应 channel_members 表。
 * (channel_id, user_id) 组合唯一：同一个人在同一频道只能有一条记录。
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "channel_members", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"channel_id", "user_id"})
})
public class ChannelMember {

    /** 成员记录 ID，数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属频道 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    /** 成员用户 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 该用户在此频道中的角色，默认普通成员 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role = MemberRole.MEMBER;

    /** 该用户能看到多少历史消息，默认全部 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HistoryLevel historyLevel = HistoryLevel.ALL;

    /** 可查看的历史条数上限，仅 historyLevel 为 LIMITED 时有值 */
    private Integer historyLimit;

    /** 入群时间。historyLevel 为 NONE 时，它是"能看到哪些消息"的分界点 */
    @Column(nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    /** JPA 反射创建实体时使用 */
    public ChannelMember() {}

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public MemberRole getRole() { return role; }
    public void setRole(MemberRole role) { this.role = role; }
    public HistoryLevel getHistoryLevel() { return historyLevel; }
    public void setHistoryLevel(HistoryLevel historyLevel) { this.historyLevel = historyLevel; }
    public Integer getHistoryLimit() { return historyLimit; }
    public void setHistoryLimit(Integer historyLimit) { this.historyLimit = historyLimit; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
