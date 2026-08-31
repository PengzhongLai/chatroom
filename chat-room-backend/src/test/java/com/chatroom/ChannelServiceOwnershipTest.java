package com.chatroom;

import com.chatroom.entity.Channel;
import com.chatroom.entity.ChannelMember;
import com.chatroom.entity.User;
import com.chatroom.enums.MemberRole;
import com.chatroom.repository.ChannelMemberRepository;
import com.chatroom.repository.ChannelRepository;
import com.chatroom.repository.MessageReadRepository;
import com.chatroom.repository.MessageRepository;
import com.chatroom.repository.UserRepository;
import com.chatroom.service.ChannelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelServiceOwnershipTest {

    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageReadRepository messageReadRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChannelService channelService;

    @BeforeEach
    void setUp() {
        channelService = new ChannelService(
                channelRepository,
                memberRepository,
                userRepository,
                messageRepository,
                messageReadRepository,
                messagingTemplate
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void transferOwnershipUsesLockedChannelAndUpdatesOwnerAndRoleMirrors() {
        User oldCreator = user(1L, "old-creator");
        User target = user(2L, "target");
        Channel channel = channel(10L, oldCreator);
        ChannelMember oldCreatorMember = member(channel, oldCreator, MemberRole.CREATOR);
        ChannelMember targetMember = member(channel, target, MemberRole.MEMBER);
        authenticate(oldCreator);

        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(memberRepository.findByChannelAndUser(channel, oldCreator))
                .thenReturn(Optional.of(oldCreatorMember));
        when(memberRepository.findByChannelAndUser(channel, target))
                .thenReturn(Optional.of(targetMember));
        when(memberRepository.findByChannel(channel))
                .thenReturn(List.of(oldCreatorMember, targetMember));

        channelService.transferOwnership(10L, 2L);

        assertSame(target, channel.getCreator());
        assertEquals(MemberRole.ADMIN, oldCreatorMember.getRole());
        assertEquals(MemberRole.CREATOR, targetMember.getRole());
        verify(channelRepository).findByIdForUpdate(10L);
        verify(channelRepository).save(channel);
        verify(memberRepository).save(oldCreatorMember);
        verify(memberRepository).save(targetMember);
    }

    @Test
    void transferOwnershipRejectsSelfTransferWithoutWrites() {
        User creator = user(1L, "creator");
        Channel channel = channel(10L, creator);
        authenticate(creator);
        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> channelService.transferOwnership(10L, 1L)
        );

        assertEquals("不能将频道转让给自己", error.getMessage());
        verify(channelRepository, never()).save(channel);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void transferOwnershipRejectsNonMemberWithoutWrites() {
        User creator = user(1L, "creator");
        User target = user(2L, "target");
        Channel channel = channel(10L, creator);
        ChannelMember creatorMember = member(channel, creator, MemberRole.CREATOR);
        authenticate(creator);

        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(memberRepository.findByChannelAndUser(channel, creator))
                .thenReturn(Optional.of(creatorMember));
        when(memberRepository.findByChannelAndUser(channel, target))
                .thenReturn(Optional.empty());

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> channelService.transferOwnership(10L, 2L)
        );

        assertEquals("目标用户不是频道成员", error.getMessage());
        assertSame(creator, channel.getCreator());
        assertEquals(MemberRole.CREATOR, creatorMember.getRole());
        verify(channelRepository, never()).save(channel);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void transferOwnershipTrustsCreatorIdNotStaleCreatorRole() {
        User authoritativeCreator = user(1L, "creator");
        User staleRoleHolder = user(2L, "stale");
        Channel channel = channel(10L, authoritativeCreator);
        authenticate(staleRoleHolder);
        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> channelService.transferOwnership(10L, 3L)
        );

        assertEquals("只有创建者才能转让频道", error.getMessage());
        verify(channelRepository, never()).save(channel);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void transferOwnershipRejectsInconsistentCreatorMirrorsBeforeMutation() {
        User creator = user(1L, "creator");
        User target = user(2L, "target");
        User extraCreator = user(3L, "extra");
        Channel channel = channel(10L, creator);
        ChannelMember creatorMember = member(channel, creator, MemberRole.CREATOR);
        ChannelMember targetMember = member(channel, target, MemberRole.MEMBER);
        ChannelMember extraCreatorMember = member(channel, extraCreator, MemberRole.CREATOR);
        authenticate(creator);

        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(memberRepository.findByChannelAndUser(channel, creator))
                .thenReturn(Optional.of(creatorMember));
        when(memberRepository.findByChannelAndUser(channel, target))
                .thenReturn(Optional.of(targetMember));
        when(memberRepository.findByChannel(channel))
                .thenReturn(List.of(creatorMember, targetMember, extraCreatorMember));

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> channelService.transferOwnership(10L, 2L)
        );

        assertEquals("频道所有权数据不一致，请先修复创建者角色", error.getMessage());
        assertSame(creator, channel.getCreator());
        assertEquals(MemberRole.CREATOR, creatorMember.getRole());
        assertEquals(MemberRole.MEMBER, targetMember.getRole());
        verify(channelRepository, never()).save(channel);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void adminCanKickMember() {
        User creator = user(1L, "creator");
        User admin = user(2L, "admin");
        User target = user(3L, "member");
        Channel channel = channel(10L, creator);
        ChannelMember adminMember = member(channel, admin, MemberRole.ADMIN);
        ChannelMember targetMember = member(channel, target, MemberRole.MEMBER);
        authenticate(admin);

        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));
        when(memberRepository.findByChannelAndUser(channel, admin))
                .thenReturn(Optional.of(adminMember));
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(memberRepository.findByChannelAndUser(channel, target))
                .thenReturn(Optional.of(targetMember));

        channelService.updateMember(10L, 3L, "kick");

        verify(memberRepository).delete(targetMember);
    }

    @Test
    void adminCannotKickAdmin() {
        User creator = user(1L, "creator");
        User admin = user(2L, "admin");
        User target = user(3L, "other-admin");
        Channel channel = channel(10L, creator);
        ChannelMember adminMember = member(channel, admin, MemberRole.ADMIN);
        ChannelMember targetMember = member(channel, target, MemberRole.ADMIN);
        authenticate(admin);

        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));
        when(memberRepository.findByChannelAndUser(channel, admin))
                .thenReturn(Optional.of(adminMember));
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(memberRepository.findByChannelAndUser(channel, target))
                .thenReturn(Optional.of(targetMember));

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> channelService.updateMember(10L, 3L, "kick")
        );

        assertEquals("管理员只能踢出普通成员", error.getMessage());
        verify(memberRepository, never()).delete(targetMember);
    }

    @Test
    void creatorCanKickAdmin() {
        User creator = user(1L, "creator");
        User target = user(2L, "admin");
        Channel channel = channel(10L, creator);
        ChannelMember creatorMember = member(channel, creator, MemberRole.CREATOR);
        ChannelMember targetMember = member(channel, target, MemberRole.ADMIN);
        authenticate(creator);

        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));
        when(memberRepository.findByChannelAndUser(channel, creator))
                .thenReturn(Optional.of(creatorMember));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(memberRepository.findByChannelAndUser(channel, target))
                .thenReturn(Optional.of(targetMember));

        channelService.updateMember(10L, 2L, "kick");

        verify(memberRepository).delete(targetMember);
    }

    @Test
    void genericMemberUpdateCannotChangeRoles() {
        User creator = user(1L, "creator");
        Channel channel = channel(10L, creator);
        ChannelMember creatorMember = member(channel, creator, MemberRole.CREATOR);
        authenticate(creator);

        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));
        when(memberRepository.findByChannelAndUser(channel, creator))
                .thenReturn(Optional.of(creatorMember));

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> channelService.updateMember(10L, 2L, "role")
        );

        assertEquals("通用成员接口仅支持踢出操作", error.getMessage());
        verify(memberRepository, never()).save(any());
        verify(memberRepository, never()).delete(any());
    }

    @Test
    void staleCreatorRoleDoesNotGrantCreatorOnlyOperations() {
        User authoritativeCreator = user(1L, "creator");
        User staleRoleHolder = user(2L, "stale");
        Channel channel = channel(10L, authoritativeCreator);
        authenticate(staleRoleHolder);
        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> channelService.promoteToAdmin(10L, 3L)
        );

        assertEquals("只有创建者才能执行此操作", error.getMessage());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void authoritativeCreatorCannotLeaveEvenWhenMirrorRoleIsWrong() {
        User creator = user(1L, "creator");
        Channel channel = channel(10L, creator);
        ChannelMember inconsistentMember = member(channel, creator, MemberRole.ADMIN);
        authenticate(creator);
        when(channelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(channel));
        when(memberRepository.findByChannelAndUser(channel, creator))
                .thenReturn(Optional.of(inconsistentMember));

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> channelService.leaveChannel(10L)
        );

        assertEquals("创建者不能退出，请先解散频道或转让", error.getMessage());
        verify(memberRepository, never()).delete(inconsistentMember);
    }

    private void authenticate(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId(), null, List.of())
        );
        SecurityContextHolder.setContext(context);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        return user;
    }

    private Channel channel(Long id, User creator) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setName("channel-" + id);
        channel.setCreator(creator);
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
