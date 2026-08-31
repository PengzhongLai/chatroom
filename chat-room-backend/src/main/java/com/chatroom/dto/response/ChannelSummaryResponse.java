package com.chatroom.dto.response;

import java.time.LocalDateTime;

public record ChannelSummaryResponse(
        Long id,
        String name,
        String description,
        UserSummaryResponse creator,
        boolean isPublic,
        boolean isMuted,
        LocalDateTime createdAt
) {
}
