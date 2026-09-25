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

    // 本类 = 输出边界。逐字段挑选，不用反射/自动映射，所以实体新增列不会意外外泄。
    //
    // toSummary（列表）与 toDetail（详情）的字段差异就是可见性分级：
    // toSummary 里根本没有 inviteCode 字段，从类型上就给不出入群凭证。
    // toDetail 的 includeInviteCode 只负责"给不给"，"该不该给"由 ChannelViewService
    // 的 canViewInviteCode 判断（创建者或 ADMIN 才为 true）。
    //
    // 实体里 isPublic/isMuted 是包装类型 Boolean（可能为 null），DTO 里是基本类型
    // boolean，直接赋值会自动拆箱，遇 null 抛 NPE，所以统一写 Boolean.TRUE.equals(x)。
    //
    // 性能提示：toMember 会访问 member.getChannel() 和 member.getUser() 两个 LAZY 关联，
    // 批量转换时每条记录都可能各触发一次 SELECT（N+1）。当前调用点都在事务内且
    // 单用户频道数不多，暂未暴露；若要优化应改用 JOIN FETCH 或 @EntityGraph。

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
