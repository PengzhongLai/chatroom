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

    @PostMapping
    public ApiResponse<ChannelDetailResponse> create(@Valid @RequestBody ChannelCreateRequest request) {
        return ApiResponse.success(channelViewService.create(
                request.name(), request.description(), request.resolvedIsPublic()
        ));
    }

    @GetMapping
    public ApiResponse<PageResponse<ChannelSummaryResponse>> list(
            @Valid @ModelAttribute ChannelListQuery query) {
        return ApiResponse.success(channelViewService.list(
                query.getKeyword(), query.getPage(), query.getSize()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChannelDetailResponse> detail(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        return ApiResponse.success(channelViewService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChannelDetailResponse> update(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody ChannelUpdateRequest request) {
        return ApiResponse.success(channelViewService.update(id, request.name(), request.description()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        channelService.deleteChannel(id);
        return ApiResponse.success(null);
    }

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

    @PostMapping("/join-by-code")
    public ApiResponse<ChannelMemberResponse> joinByCode(
            @Valid @RequestBody InviteCodeRequest request) {
        return ApiResponse.success(channelViewService.joinByInviteCode(request.inviteCode()));
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<Void> leave(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        channelService.leaveChannel(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/invite")
    public ApiResponse<ChannelMemberResponse> invite(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody ChannelInviteRequest request) {
        return ApiResponse.success(channelViewService.invite(
                id, request.userId(), request.resolvedHistoryLevel(), request.resolvedHistoryLimit()
        ));
    }

    @PutMapping("/{id}/mute")
    public ApiResponse<ChannelDetailResponse> toggleMute(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        return ApiResponse.success(channelViewService.toggleMute(id));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<ChannelMemberResponse>> members(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id) {
        return ApiResponse.success(channelViewService.members(id));
    }

    @PutMapping("/{id}/members/{userId}")
    public ApiResponse<Void> updateMember(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @PathVariable @Positive(message = "用户 ID 必须为正数") Long userId,
            @Valid @RequestBody MemberActionRequest request) {
        channelService.updateMember(id, userId, request.action());
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/transfer")
    public ApiResponse<Void> transferOwnership(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody UserIdRequest request) {
        channelService.transferOwnership(id, request.userId());
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/promote")
    public ApiResponse<Void> promoteToAdmin(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody UserIdRequest request) {
        channelService.promoteToAdmin(id, request.userId());
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/demote")
    public ApiResponse<Void> demoteToMember(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @RequestBody UserIdRequest request) {
        channelService.demoteToMember(id, request.userId());
        return ApiResponse.success(null);
    }

    @GetMapping("/my")
    public ApiResponse<List<ChannelMemberResponse>> myChannels() {
        return ApiResponse.success(channelViewService.myChannels());
    }

    // --- Message endpoints ---

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageResponse>> getMessages(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @Valid @ModelAttribute MessagePaginationRequest pagination) {
        return ApiResponse.success(messageService.getMessages(
                id, pagination.getPage(), pagination.getSize()
        ));
    }

    @PutMapping("/{id}/messages/{msgId}/recall")
    public ApiResponse<Void> recallMessage(
            @PathVariable @Positive(message = "频道 ID 必须为正数") Long id,
            @PathVariable @Positive(message = "消息 ID 必须为正数") Long msgId) {
        messageService.recallChannelMessage(id, msgId);
        return ApiResponse.success(null);
    }
}
