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

/**
 * 频道的读接口与返回形态组装。负责开启事务，并把 Service 返回的实体转换成响应对象。
 * 命名里的 View 指"返回给调用者的数据形态"，与前端 Vue 无关。
 */
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

    /** 创建频道并返回频道详情。创建者从登录身份取得，返回内容包含邀请码 */
    @Transactional
    public ChannelDetailResponse create(String name, String description, boolean isPublic) {
        return channelResponseMapper.toDetail(channelService.createChannel(name, description, isPublic), true);
    }

    /** 分页查询公开频道列表，返回列表用的频道摘要 */
    @Transactional(readOnly = true)
    public PageResponse<ChannelSummaryResponse> list(String keyword, int page, int size) {
        return channelResponseMapper.toPage(channelService.listChannels(keyword, page, size));
    }

    /** 查询频道详情。先判断当前用户有无权限看邀请码，再决定响应里给不给 */
    @Transactional(readOnly = true)
    public ChannelDetailResponse detail(Long channelId) {
        Channel channel = channelService.getChannel(channelId);
        return channelResponseMapper.toDetail(channel, canViewInviteCode(channel));
    }

    /** 修改频道名称或描述并返回最新详情。能修改就说明有权看邀请码，固定返回 */
    @Transactional
    public ChannelDetailResponse update(Long channelId, String name, String description) {
        return channelResponseMapper.toDetail(
                channelService.updateChannel(channelId, name, description),
                true
        );
    }

    /** 加入公开频道，返回加入后的成员信息 */
    @Transactional
    public ChannelMemberResponse join(Long channelId) {
        return channelResponseMapper.toMember(channelService.joinChannel(channelId));
    }

    /** 凭邀请码加入频道，返回加入后的成员信息 */
    @Transactional
    public ChannelMemberResponse joinByInviteCode(String inviteCode) {
        return channelResponseMapper.toMember(channelService.joinByInviteCode(inviteCode));
    }

    /** 邀请用户进频道，可指定他能看到的历史范围，返回新成员信息 */
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

    /** 切换全员禁言开关并返回频道详情 */
    @Transactional
    public ChannelDetailResponse toggleMute(Long channelId) {
        return channelResponseMapper.toDetail(channelService.toggleMute(channelId), true);
    }

    /** 查询频道成员列表 */
    @Transactional(readOnly = true)
    public List<ChannelMemberResponse> members(Long channelId) {
        return channelResponseMapper.toMembers(channelService.listMembers(channelId));
    }

    /** 查询当前用户加入的频道列表 */
    @Transactional(readOnly = true)
    public List<ChannelMemberResponse> myChannels() {
        return channelResponseMapper.toMembers(channelService.myChannels());
    }

    /** 判断当前用户能否看到该频道的邀请码：创建者或管理员可以，其他人不行 */
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
