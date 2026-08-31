package com.chatroom;

import com.chatroom.entity.Channel;
import com.chatroom.entity.ChannelMember;
import com.chatroom.entity.Message;
import com.chatroom.entity.User;
import com.chatroom.enums.MemberRole;
import com.chatroom.enums.MessageType;
import com.chatroom.mapper.MessageResponseMapper;
import com.chatroom.repository.ChannelMemberRepository;
import com.chatroom.repository.ChannelRepository;
import com.chatroom.repository.MessageReadRepository;
import com.chatroom.repository.MessageRepository;
import com.chatroom.repository.UserRepository;
import com.chatroom.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceCreatorAuthorityTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageReadRepository messageReadRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private MessageResponseMapper messageResponseMapper;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository,
                messageReadRepository,
                channelRepository,
                memberRepository,
                userRepository,
                messagingTemplate,
                messageResponseMapper
        );
    }

    @Test
    void authoritativeCreatorCanSendInMutedChannelEvenIfMirrorRoleIsWrong() {
        User creator = user(1L);
        Channel channel = mutedChannel(10L, creator);
        ChannelMember creatorMember = member(channel, creator, MemberRole.MEMBER);

        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));
        when(memberRepository.existsByChannelAndUser(channel, creator)).thenReturn(true);
        when(memberRepository.findByChannelAndUser(channel, creator))
                .thenReturn(Optional.of(creatorMember));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(99L);
            return message;
        });
        when(messageResponseMapper.toPayload(any(Message.class)))
                .thenReturn(Map.of("id", 99L));

        Map<String, Object> result = messageService.sendMessage(
                1L, 10L, "hello", MessageType.TEXT, null, null
        );

        assertFalse(result.isEmpty());
        assertEquals(99L, result.get("id"));
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void staleCreatorRoleDoesNotBypassMutedChannel() {
        User creator = user(1L);
        User staleRoleHolder = user(2L);
        Channel channel = mutedChannel(10L, creator);
        ChannelMember staleCreatorMember = member(channel, staleRoleHolder, MemberRole.CREATOR);

        when(userRepository.findById(2L)).thenReturn(Optional.of(staleRoleHolder));
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));
        when(memberRepository.existsByChannelAndUser(channel, staleRoleHolder)).thenReturn(true);
        when(memberRepository.findByChannelAndUser(channel, staleRoleHolder))
                .thenReturn(Optional.of(staleCreatorMember));

        Map<String, Object> result = messageService.sendMessage(
                2L, 10L, "hello", MessageType.TEXT, null, null
        );

        assertTrue(result.isEmpty());
        verify(messageRepository, never()).save(any(Message.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq("2"), eq("/queue/errors"), any(Map.class)
        );
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setNickname("user-" + id);
        return user;
    }

    private Channel mutedChannel(Long id, User creator) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setName("channel-" + id);
        channel.setCreator(creator);
        channel.setIsMuted(true);
        return channel;
    }

    private ChannelMember member(Channel channel, User user, MemberRole role) {
        ChannelMember member = new ChannelMember();
        member.setChannel(channel);
        member.setUser(user);
        member.setRole(role);
        return member;
    }
}
