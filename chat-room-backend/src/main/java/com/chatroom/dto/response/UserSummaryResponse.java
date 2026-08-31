package com.chatroom.dto.response;

public record UserSummaryResponse(
        Long id,
        String username,
        String nickname,
        String avatarUrl
) {
}
