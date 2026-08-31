package com.chatroom.dto.response;

import java.time.LocalDateTime;

public record ChannelDetailResponse(
        Long id,
        String name,
        String description,
        UserSummaryResponse creator,
        boolean isPublic,
        String inviteCode,
        boolean isMuted,
        LocalDateTime createdAt
) {
}
