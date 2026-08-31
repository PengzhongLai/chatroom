package com.chatroom.dto.response;

public record MentionResponse(
        Long userId,
        String username,
        String nickname
) {
}
