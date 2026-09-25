package com.chatroom.repository;

import com.chatroom.entity.Channel;
import com.chatroom.entity.ChannelMember;
import com.chatroom.entity.User;
import com.chatroom.enums.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {

    // 同一个查询有两种传参风格，生成的 SQL 完全相同，区别只在调用方手上有什么：
    //   findByChannelAndUser(实体, 实体)      —— 已经查出对象时用
    //   existsByChannel_IdAndUser_Id(ID, ID)  —— 只有 ID 时用（如 StompInterceptor 校验订阅权限）
    // 方法名里的下划线是 Spring Data 语法，表示"顺着关联取它的 id 字段"。
    //
    // deleteBy* 是派生删除：Spring Data 会先 SELECT 查出实体再逐个 DELETE，
    // 必须在事务内调用，否则抛 TransactionRequiredException。

    Optional<ChannelMember> findByChannelAndUser(Channel channel, User user);
    List<ChannelMember> findByChannel(Channel channel);
    List<ChannelMember> findByUser(User user);
    long countByChannel(Channel channel);
    boolean existsByChannelAndUser(Channel channel, User user);
    boolean existsByChannel_IdAndUser_Id(Long channelId, Long userId);
    boolean existsByChannelAndUserAndRole(Channel channel, User user, MemberRole role);
    void deleteByChannelAndUser(Channel channel, User user);
    void deleteByChannel(Channel channel);
}
