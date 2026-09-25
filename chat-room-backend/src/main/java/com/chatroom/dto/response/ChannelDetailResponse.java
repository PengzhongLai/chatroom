package com.chatroom.dto.response;

import java.time.LocalDateTime;

/**
 * 频道详情响应。
 */
public record ChannelDetailResponse(
        /** 频道 ID */
        Long id,
        /** 频道名称 */
        String name,
        /** 频道描述 */
        String description,
        /** 创建者，只含 ID、用户名、昵称、头像 */
        UserSummaryResponse creator,
        /** 是否公开频道 */
        boolean isPublic,
        /** 邀请码。调用者无权查看时为 null */
        String inviteCode,
        /** 是否全员禁言 */
        boolean isMuted,
        /** 创建时间 */
        LocalDateTime createdAt
) {
}
