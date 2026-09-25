package com.chatroom.repository;

import com.chatroom.entity.Channel;
import com.chatroom.entity.ChannelMember;
import com.chatroom.entity.User;
import com.chatroom.enums.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * channel_members 表的数据库访问接口。方法名由 Spring Data 解析成查询语句。
 */
public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {

    /** 查询某个用户在某个频道里的成员记录，不存在时返回空 Optional */
    Optional<ChannelMember> findByChannelAndUser(Channel channel, User user);

    /** 查询一个频道的全部成员 */
    List<ChannelMember> findByChannel(Channel channel);

    /** 查询一个用户加入的全部频道成员记录 */
    List<ChannelMember> findByUser(User user);

    /** 统计一个频道有多少成员 */
    long countByChannel(Channel channel);

    /** 判断某个用户是否是某个频道的成员 */
    boolean existsByChannelAndUser(Channel channel, User user);

    /** 同上，但只传 ID，用于手上没有实体对象的场景 */
    boolean existsByChannel_IdAndUser_Id(Long channelId, Long userId);

    /** 判断某个用户在某个频道里是否担任指定角色 */
    boolean existsByChannelAndUserAndRole(Channel channel, User user, MemberRole role);

    /** 删除某个用户在某个频道里的成员记录 */
    void deleteByChannelAndUser(Channel channel, User user);

    /** 删除一个频道的全部成员记录（解散频道时使用） */
    void deleteByChannel(Channel channel);
}
