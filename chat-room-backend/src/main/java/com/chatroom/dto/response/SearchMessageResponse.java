package com.chatroom.dto.response;

import java.time.LocalDateTime;

public record SearchMessageResponse(
        Long id,
        Long channelId,
        Long chatId,
        Sender sender,
        String type,
        String content,
        String fileName,
        String filePath,
        boolean isRecalled,
        LocalDateTime createdAt,
        String context,
        Long contextId,
        String contextName
) {
    public record Sender(Long id, String username, String nickname) {
    }
}
