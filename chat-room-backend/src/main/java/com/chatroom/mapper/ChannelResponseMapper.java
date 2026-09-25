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

/**
 * 把频道相关的实体转换成接口响应对象。
 * toSummary 用于列表，toDetail 用于详情（可按权限决定是否包含邀请码）。
 */
@Component
public class ChannelResponseMapper {

    private final UserResponseMapper userResponseMapper;

    public ChannelResponseMapper(UserResponseMapper userResponseMapper) {
        this.userResponseMapper = userResponseMapper;
    }

    /** 转成频道摘要，用于列表展示。不含邀请码 */
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

    /** 转成频道详情，默认包含邀请码（用于创建、修改等有权限的场景） */
    public ChannelDetailResponse toDetail(Channel channel) {
        return toDetail(channel, true);
    }

    /** 转成频道详情。includeInviteCode 为 false 时邀请码返回 null */
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

    /** 转成成员响应，内含频道基本信息和成员的用户摘要 */
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

    /** 批量转成成员响应列表 */
    public List<ChannelMemberResponse> toMembers(List<ChannelMember> members) {
        return members.stream().map(this::toMember).toList();
    }

    /** 把分页查询结果转成分页响应，保留页码、总条数和总页数 */
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
