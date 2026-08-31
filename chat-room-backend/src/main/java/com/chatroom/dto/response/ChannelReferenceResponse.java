package com.chatroom.dto.response;

public record ChannelReferenceResponse(
        Long id,
        String name,
        boolean isPublic,
        boolean isMuted
) {
}
