package com.chatroom.entity;

import com.chatroom.enums.HistoryLevel;
import com.chatroom.enums.MemberRole;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "channel_members", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"channel_id", "user_id"})
})
public class ChannelMember {

    // uniqueConstraints 是"列组合"唯一，不是两列各自唯一：
    // 同一个人在同一频道只能有一条记录，但一个人可以进多个频道、一个频道可以有多人。
    // columnNames 里写的是数据库列名（下划线），不是 Java 字段名。
    //
    // role 是镜像字段（权威见 Channel.creator_id 处的说明）。
    // joinedAt 不只是入群时间，还是 HistoryLevel.NONE 的查询分界（created_at >= joined_at）。
    // historyLimit 仅对 LIMITED 有意义，所以可空，使用方必须处理 null（默认按 50）。

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role = MemberRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HistoryLevel historyLevel = HistoryLevel.ALL;

    private Integer historyLimit;

    @Column(nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

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
