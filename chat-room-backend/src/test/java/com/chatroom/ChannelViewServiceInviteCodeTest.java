package com.chatroom;

import com.chatroom.entity.Channel;
import com.chatroom.entity.ChannelMember;
import com.chatroom.entity.User;
import com.chatroom.enums.MemberRole;
import com.chatroom.mapper.ChannelResponseMapper;
import com.chatroom.mapper.UserResponseMapper;
import com.chatroom.repository.ChannelMemberRepository;
import com.chatroom.service.ChannelService;
import com.chatroom.service.ChannelViewService;
import com.chatroom.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelViewServiceInviteCodeTest {

    private final ChannelService channelService = mock(ChannelService.class);
    private final ChannelMemberRepository channelMemberRepository = mock(ChannelMemberRepository.class);
    private final UserService userService = mock(UserService.class);
    private ChannelViewService channelViewService;
    private Channel channel;
    private User creator;
    private User viewer;

    @BeforeEach
    void setUp() {
        channelViewService = new ChannelViewService(
                channelService,
                new ChannelResponseMapper(new UserResponseMapper()),
                channelMemberRepository,
                userService
        );
        creator = user(1L, "creator");
        viewer = user(2L, "viewer");
        channel = new Channel();
        channel.setId(10L);
        channel.setName("private");
        channel.setCreator(creator);
        channel.setIsPublic(false);
        channel.setIsMuted(false);
        channel.setInviteCode("secret-code");
        when(channelService.getChannel(10L)).thenReturn(channel);
        when(userService.getCurrentUser()).thenReturn(viewer);
    }

    @Test
    void ordinaryMemberCannotSeeInviteCode() {
        when(channelMemberRepository.findByChannelAndUser(channel, viewer))
                .thenReturn(Optional.of(member(MemberRole.MEMBER)));

        assertThat(channelViewService.detail(10L).inviteCode()).isNull();
    }

    @Test
    void administratorCanSeeInviteCode() {
        when(channelMemberRepository.findByChannelAndUser(channel, viewer))
                .thenReturn(Optional.of(member(MemberRole.ADMIN)));

        assertThat(channelViewService.detail(10L).inviteCode()).isEqualTo("secret-code");
    }

    @Test
    void authoritativeCreatorCanSeeInviteCodeWithoutTrustingMirrorRole() {
        when(userService.getCurrentUser()).thenReturn(creator);

        assertThat(channelViewService.detail(10L).inviteCode()).isEqualTo("secret-code");
    }

    private ChannelMember member(MemberRole role) {
        ChannelMember member = new ChannelMember();
        member.setChannel(channel);
        member.setUser(viewer);
        member.setRole(role);
        return member;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        return user;
    }
}
