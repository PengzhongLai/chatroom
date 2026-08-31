package com.chatroom.mapper;

import com.chatroom.dto.response.ChannelDetailResponse;
import com.chatroom.dto.response.ChannelMemberResponse;
import com.chatroom.dto.response.ChannelReferenceResponse;
import com.chatroom.dto.response.ChannelSummaryResponse;
import com.chatroom.dto.response.PageResponse;
import com.chatroom.entity.Channel;
import com.chatroom.entity.ChannelMember;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChannelResponseMapper {

    private final UserResponseMapper userResponseMapper;

    public ChannelResponseMapper(UserResponseMapper userResponseMapper) {
        this.userResponseMapper = userResponseMapper;
    }

    public ChannelSummaryResponse toSummary(Channel channel) {
        return new ChannelSummaryResponse(
                channel.getId(),
                channel.getName(),
                channel.getDescription(),
                userResponseMapper.toSummary(channel.getCreator()),
                Boolean.TRUE.equals(channel.getIsPublic()),
                Boolean.TRUE.equals(channel.getIsMuted()),
                channel.getCreatedAt()
        );
    }

    public ChannelDetailResponse toDetail(Channel channel) {
        return toDetail(channel, true);
    }

    public ChannelDetailResponse toDetail(Channel channel, boolean includeInviteCode) {
        return new ChannelDetailResponse(
                channel.getId(),
                channel.getName(),
                channel.getDescription(),
                userResponseMapper.toSummary(channel.getCreator()),
                Boolean.TRUE.equals(channel.getIsPublic()),
                includeInviteCode ? channel.getInviteCode() : null,
                Boolean.TRUE.equals(channel.getIsMuted()),
                channel.getCreatedAt()
        );
    }

    public ChannelMemberResponse toMember(ChannelMember member) {
        Channel channel = member.getChannel();
        ChannelReferenceResponse channelResponse = new ChannelReferenceResponse(
                channel.getId(),
                channel.getName(),
                Boolean.TRUE.equals(channel.getIsPublic()),
                Boolean.TRUE.equals(channel.getIsMuted())
        );
        return new ChannelMemberResponse(
                member.getId(),
                channelResponse,
                userResponseMapper.toSummary(member.getUser()),
                member.getRole(),
                member.getHistoryLevel(),
                member.getHistoryLimit(),
                member.getJoinedAt()
        );
    }

    public List<ChannelMemberResponse> toMembers(List<ChannelMember> members) {
        return members.stream().map(this::toMember).toList();
    }

    public PageResponse<ChannelSummaryResponse> toPage(Page<Channel> channels) {
        List<ChannelSummaryResponse> content = channels.getContent().stream()
                .map(this::toSummary)
                .toList();
        PageResponse.PageMetadata metadata = new PageResponse.PageMetadata(
                channels.getSize(),
                channels.getNumber(),
                channels.getTotalElements(),
                channels.getTotalPages()
        );
        return new PageResponse<>(content, metadata);
    }
}
