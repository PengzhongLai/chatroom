package com.chatroom.dto.response;

import com.chatroom.enums.MessageType;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        Long channelId,
        Long chatId,
        UserSummaryResponse sender,
        MessageType type,
        String content,
        String fileName,
        String filePath,
        boolean isRecalled,
        LocalDateTime createdAt,
        List<MentionResponse> mentions
) {
}
