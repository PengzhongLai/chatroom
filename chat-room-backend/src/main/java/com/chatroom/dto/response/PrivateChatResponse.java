package com.chatroom.dto.response;

import com.chatroom.enums.ChatStatus;

import java.time.LocalDateTime;

public record PrivateChatResponse(
        Long id,
        Long initiatorId,
        boolean iAmInitiator,
        ChatStatus status,
        UserSummaryResponse otherUser,
        String lastMessage,
        LocalDateTime lastMessageTime,
        Long lastSenderId
) {
}
