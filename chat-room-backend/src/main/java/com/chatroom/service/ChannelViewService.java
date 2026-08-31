package com.chatroom.service;

import com.chatroom.dto.response.ChannelDetailResponse;
import com.chatroom.dto.response.ChannelMemberResponse;
import com.chatroom.dto.response.ChannelSummaryResponse;
import com.chatroom.dto.response.PageResponse;
import com.chatroom.enums.HistoryLevel;
import com.chatroom.enums.MemberRole;
import com.chatroom.mapper.ChannelResponseMapper;
import com.chatroom.entity.Channel;
import com.chatroom.entity.User;
import com.chatroom.repository.ChannelMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChannelViewService {

    private final ChannelService channelService;
    private final ChannelResponseMapper channelResponseMapper;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserService userService;

    public ChannelViewService(
            ChannelService channelService,
            ChannelResponseMapper channelResponseMapper,
            ChannelMemberRepository channelMemberRepository,
            UserService userService
    ) {
        this.channelService = channelService;
        this.channelResponseMapper = channelResponseMapper;
        this.channelMemberRepository = channelMemberRepository;
        this.userService = userService;
    }

    @Transactional
    public ChannelDetailResponse create(String name, String description, boolean isPublic) {
        return channelResponseMapper.toDetail(channelService.createChannel(name, description, isPublic), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChannelSummaryResponse> list(String keyword, int page, int size) {
        return channelResponseMapper.toPage(channelService.listChannels(keyword, page, size));
    }

    @Transactional(readOnly = true)
    public ChannelDetailResponse detail(Long channelId) {
        Channel channel = channelService.getChannel(channelId);
        return channelResponseMapper.toDetail(channel, canViewInviteCode(channel));
    }

    @Transactional
    public ChannelDetailResponse update(Long channelId, String name, String description) {
        return channelResponseMapper.toDetail(
                channelService.updateChannel(channelId, name, description),
                true
        );
    }

    @Transactional
    public ChannelMemberResponse join(Long channelId) {
        return channelResponseMapper.toMember(channelService.joinChannel(channelId));
    }

    @Transactional
    public ChannelMemberResponse joinByInviteCode(String inviteCode) {
        return channelResponseMapper.toMember(channelService.joinByInviteCode(inviteCode));
    }

    @Transactional
    public ChannelMemberResponse invite(
            Long channelId,
            Long userId,
            HistoryLevel historyLevel,
            Integer historyLimit
    ) {
        return channelResponseMapper.toMember(
                channelService.inviteMember(channelId, userId, historyLevel, historyLimit)
        );
    }

    @Transactional
    public ChannelDetailResponse toggleMute(Long channelId) {
        return channelResponseMapper.toDetail(channelService.toggleMute(channelId), true);
    }

    @Transactional(readOnly = true)
    public List<ChannelMemberResponse> members(Long channelId) {
        return channelResponseMapper.toMembers(channelService.listMembers(channelId));
    }

    @Transactional(readOnly = true)
    public List<ChannelMemberResponse> myChannels() {
        return channelResponseMapper.toMembers(channelService.myChannels());
    }

    private boolean canViewInviteCode(Channel channel) {
        User viewer = userService.getCurrentUser();
        if (channel.getCreator().getId().equals(viewer.getId())) {
            return true;
        }
        return channelMemberRepository.findByChannelAndUser(channel, viewer)
                .map(member -> member.getRole() == MemberRole.ADMIN)
                .orElse(false);
    }
}
