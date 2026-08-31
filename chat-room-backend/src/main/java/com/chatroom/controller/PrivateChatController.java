package com.chatroom.controller;

import com.chatroom.dto.ApiResponse;
import com.chatroom.dto.request.MessagePaginationRequest;
import com.chatroom.dto.request.PrivateChatCreateRequest;
import com.chatroom.dto.request.PrivateMessageSendRequest;
import com.chatroom.dto.response.MessageResponse;
import com.chatroom.dto.response.PrivateChatResponse;
import com.chatroom.service.PrivateChatService;
import com.chatroom.service.PrivateChatViewService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.List;

@Controller
@Validated
public class PrivateChatController {

    private final PrivateChatService privateChatService;
    private final PrivateChatViewService privateChatViewService;

    public PrivateChatController(
            PrivateChatService privateChatService,
            PrivateChatViewService privateChatViewService
    ) {
        this.privateChatService = privateChatService;
        this.privateChatViewService = privateChatViewService;
    }

    // --- REST API ---

    @PostMapping("/api/private-chats")
    @ResponseBody
    public ApiResponse<PrivateChatResponse> initiate(
            @Valid @RequestBody PrivateChatCreateRequest request) {
        return ApiResponse.success(privateChatViewService.initiate(request.targetUserId()));
    }

    @GetMapping("/api/private-chats")
    @ResponseBody
    public ApiResponse<List<PrivateChatResponse>> list() {
        return ApiResponse.success(privateChatService.getChats());
    }

    @GetMapping("/api/private-chats/{id}/messages")
    @ResponseBody
    public ApiResponse<List<MessageResponse>> getMessages(
            @PathVariable @Positive(message = "私聊 ID 必须为正数") Long id,
            @Valid @ModelAttribute MessagePaginationRequest pagination) {
        return ApiResponse.success(privateChatService.getMessages(
                id, pagination.getPage(), pagination.getSize()
        ));
    }

    @PostMapping("/api/private-chats/{id}/accept")
    @ResponseBody
    public ApiResponse<PrivateChatResponse> accept(
            @PathVariable @Positive(message = "私聊 ID 必须为正数") Long id) {
        return ApiResponse.success(privateChatViewService.accept(id));
    }

    @PostMapping("/api/private-chats/{id}/reject")
    @ResponseBody
    public ApiResponse<Void> reject(
            @PathVariable @Positive(message = "私聊 ID 必须为正数") Long id) {
        privateChatService.rejectChat(id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/private-chats/{id}")
    @ResponseBody
    public ApiResponse<Void> delete(
            @PathVariable @Positive(message = "私聊 ID 必须为正数") Long id) {
        privateChatService.deleteChat(id);
        return ApiResponse.success(null);
    }

    // --- WebSocket ---

    @MessageMapping("/private.send")
    public void handleSend(
            @Valid @Payload PrivateMessageSendRequest request,
            Principal principal) {
        Long senderId = Long.parseLong(principal.getName());
        privateChatService.sendMessage(
                request.chatId(),
                senderId,
                request.content(),
                request.resolvedType(),
                request.fileName(),
                request.filePath()
        );
    }
}
