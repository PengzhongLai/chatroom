package com.chatroom.dto.response;

import com.chatroom.enums.HistoryLevel;
import com.chatroom.enums.MemberRole;

import java.time.LocalDateTime;

public record ChannelMemberResponse(
        Long id,
        ChannelReferenceResponse channel,
        UserSummaryResponse user,
        MemberRole role,
        HistoryLevel historyLevel,
        Integer historyLimit,
        LocalDateTime joinedAt
) {
}
