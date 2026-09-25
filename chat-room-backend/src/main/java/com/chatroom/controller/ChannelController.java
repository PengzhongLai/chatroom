package com.chatroom.controller;

import com.chatroom.dto.ApiResponse;
import com.chatroom.dto.request.ChannelCreateRequest;
import com.chatroom.dto.request.ChannelInviteRequest;
import com.chatroom.dto.request.ChannelListQuery;
import com.chatroom.dto.request.ChannelUpdateRequest;
import com.chatroom.dto.request.InviteCodeRequest;
import com.chatroom.dto.request.JoinChannelRequest;
import com.chatroom.dto.request.MemberActionRequest;
import com.chatroom.dto.request.MessagePaginationRequest;
import com.chatroom.dto.request.UserIdRequest;
import com.chatroom.dto.response.ChannelDetailResponse;
import com.chatroom.dto.response.ChannelMemberResponse;
import com.chatroom.dto.response.ChannelSummaryResponse;
import com.chatroom.dto.response.MessageResponse;
import com.chatroom.dto.response.PageResponse;
import com.chatroom.service.ChannelService;
import com.chatroom.service.ChannelViewService;
import com.chatroom.service.MessageService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 频道相关的 HTTP 接口：频道的增删改查、加入退出、成员与角色管理、频道消息历史。
 * 只负责接收请求、触发参数校验、把结果包成 ApiResponse，业务规则都在 Service。
 */
@RestController
@RequestMapping("/api/channels")
@Validated
public class ChannelController {

    private final ChannelService channelService;
    private final ChannelViewService channelViewService;
    private final MessageService messageService;

    public ChannelController(
            ChannelService channelService,
            ChannelViewService channelViewService,
            MessageService messageService
    ) {
        this.channelService = channelService;
        this.channelViewService = channelViewService;
        this.messageService = messageService;
    }

    /** 创建频道。创建者取自登录身份，请求体只提供名称、描述和是否公开 */
    @PostMapping
    public ApiResponse<ChannelDetailResponse> create(@Valid @RequestBody ChannelCreateRequest request) {
        return ApiResponse.success(channelViewService.create(
                request.name(), request.description(), request.resolvedIsPublic()
        ));
    }

    /** 分页查询公开频道列表，可按名称关键词过滤 */
    @GetMapping
    public ApiResponse<PageResponse<ChannelSummaryResponse>> list(
            @Valid @ModelAttribute ChannelListQuery query) {
        return ApiResponse.success(channelViewService.list(
                query.getKeyword(), query.getPage(), query.getSize()
        ));
    }

    /** 查询频道详情。邀请码只有创建者和管理员能看到，其他人拿到的是 null */
    @GetMapping("/{id}")
    public ApiResponse<ChannelDetailResponse> detail(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        return ApiResponse.success(channelViewService.detail(id));
    }

    /** 修改频道名称或描述，仅创建者和管理员可操作 */
    @PutMapping("/{id}")
    public ApiResponse<ChannelDetailResponse> update(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody ChannelUpdateRequest request) {
        return ApiResponse.success(channelViewService.update(id, request.name(), request.description()));
    }

    /** 解散频道，仅创建者可操作。会一并清除该频道的成员、消息和已读记录 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        channelService.deleteChannel(id);
        return ApiResponse.success(null);
    }

    /** 加入频道。带邀请码时按邀请码加入，否则按路径上的频道 ID 加入 */
    @PostMapping("/{id}/join")
    public ApiResponse<ChannelMemberResponse> join(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody(required = false) JoinChannelRequest request
    ) {
        if (request != null && request.inviteCode() != null) {
            return ApiResponse.success(channelViewService.joinByInviteCode(request.inviteCode()));
        }
        return ApiResponse.success(channelViewService.join(id));
    }

    /** 直接凭邀请码加入频道，不需要知道频道 ID */
    @PostMapping("/join-by-code")
    public ApiResponse<ChannelMemberResponse> joinByCode(
            @Valid @RequestBody InviteCodeRequest request) {
        return ApiResponse.success(channelViewService.joinByInviteCode(request.inviteCode()));
    }

    /** 退出频道。创建者不能退出，需要先转让或解散 */
    @PostMapping("/{id}/leave")
    public ApiResponse<Void> leave(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        channelService.leaveChannel(id);
        return ApiResponse.success(null);
    }

    /** 把指定用户邀请进频道，可同时设定他能看到的历史消息范围 */
    @PostMapping("/{id}/invite")
    public ApiResponse<ChannelMemberResponse> invite(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody ChannelInviteRequest request) {
        return ApiResponse.success(channelViewService.invite(
                id, request.userId(), request.resolvedHistoryLevel(), request.resolvedHistoryLimit()
        ));
    }

    /** 切换全员禁言开关，并广播一条系统消息 */
    @PutMapping("/{id}/mute")
    public ApiResponse<ChannelDetailResponse> toggleMute(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        return ApiResponse.success(channelViewService.toggleMute(id));
    }

    /** 查询频道成员列表 */
    @GetMapping("/{id}/members")
    public ApiResponse<List<ChannelMemberResponse>> members(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        return ApiResponse.success(channelViewService.members(id));
    }

    /** 对成员执行操作，目前仅支持踢出（action 传 kick） */
    @PutMapping("/{id}/members/{userId}")
    public ApiResponse<Void> updateMember(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @PathVariable @Positive(message = "用户 ID 必须为正数") Long userId,
            @Valid @RequestBody MemberActionRequest request) {
        channelService.updateMember(id, userId, request.action());
        return ApiResponse.success(null);
    }

    /** 转让频道所有权，仅创建者可操作 */
    @PutMapping("/{id}/transfer")
    public ApiResponse<Void> transferOwnership(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody UserIdRequest request) {
        channelService.transferOwnership(id, request.userId());
        return ApiResponse.success(null);
    }

    /** 把成员提升为管理员，仅创建者可操作 */
    @PutMapping("/{id}/promote")
    public ApiResponse<Void> promoteToAdmin(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody UserIdRequest request) {
        channelService.promoteToAdmin(id, request.userId());
        return ApiResponse.success(null);
    }

    /** 把管理员降为普通成员，仅创建者可操作 */
    @PutMapping("/{id}/demote")
    public ApiResponse<Void> demoteToMember(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody UserIdRequest request) {
        channelService.demoteToMember(id, request.userId());
        return ApiResponse.success(null);
    }

    /** 查询当前登录用户加入的全部频道 */
    @GetMapping("/my")
    public ApiResponse<List<ChannelMemberResponse>> myChannels() {
        return ApiResponse.success(channelViewService.myChannels());
    }

    // --- Message endpoints ---

    /** 分页查询频道历史消息，能查到哪些消息由成员的历史级别决定 */
    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageResponse>> getMessages(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @ModelAttribute MessagePaginationRequest pagination) {
        return ApiResponse.success(messageService.getMessages(
                id, pagination.getPage(), pagination.getSize()
        ));
    }

    /** 撤回频道消息，仅消息发送者本人可操作 */
    @PutMapping("/{id}/messages/{msgId}/recall")
    public ApiResponse<Void> recallMessage(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @PathVariable @Positive(message = "消息 ID 必须为正数") Long msgId) {
        messageService.recallChannelMessage(id, msgId);
        return ApiResponse.success(null);
    }
}
