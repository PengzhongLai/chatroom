package com.chatroom.controller;

import com.chatroom.dto.request.ChannelMessageSendRequest;
import com.chatroom.dto.request.ChatReadRequest;
import com.chatroom.dto.request.ChatTypingRequest;
import com.chatroom.dto.request.MessageRecallRequest;
import com.chatroom.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket 消息控制器。
 * 处理所有 STOMP 消息路由：发送、撤回、输入中提示和已读回执。
 * 注意：WebSocket 线程无 SecurityContext，所有方法通过 Principal 获取 userId。
 */
@Controller
public class ChatController {

    private final MessageService messageService;

    public ChatController(MessageService messageService) {
        this.messageService = messageService;
    }

    /** 处理频道消息发送。前端 SEND /app/chat.send → 此处，携带 { channelId, content, type?, fileName?, filePath? } */
    @MessageMapping("/chat.send")
    public void handleSend(
            @Valid @Payload ChannelMessageSendRequest request,
            Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        messageService.sendMessage(
                userId,
                request.channelId(),
                request.content(),
                request.resolvedType(),
                request.fileName(),
                request.filePath()
        );
    }

    /** 处理消息撤回。仅发送者可撤回，前端 SEND /app/chat.recall { messageId } */
    @MessageMapping("/chat.recall")
    public void handleRecall(
            @Valid @Payload MessageRecallRequest request,
            Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        messageService.recallMessage(userId, request.messageId());
    }

    /** 处理输入中状态。前端每 2 秒发送一次 typing=true，停止输入 3 秒后发 typing=false */
    @MessageMapping("/chat.typing")
    public void handleTyping(
            @Valid @Payload ChatTypingRequest request,
            Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        messageService.sendTypingNotification(userId, request.channelId(), request.typing());
    }

    /** 处理已读回执。前端进入频道时自动发送最后一条消息的 ID */
    @MessageMapping("/chat.read")
    public void handleRead(
            @Valid @Payload ChatReadRequest request,
            Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        messageService.markAsRead(userId, request.messageId());
    }
}
