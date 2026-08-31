package com.chatroom.repository;

import com.chatroom.entity.Channel;
import com.chatroom.entity.ChannelMember;
import com.chatroom.entity.User;
import com.chatroom.enums.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
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
