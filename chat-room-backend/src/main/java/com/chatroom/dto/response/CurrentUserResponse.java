package com.chatroom.dto.response;

import com.chatroom.enums.UserStatus;

public record CurrentUserResponse(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        UserStatus status,
        String theme
) {
}
