package com.chatroom.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatReadRequest(
        @NotNull(message = "频道 ID 不能为空")
        @Positive(message = "频道 ID 必须为正数")
        Long channelId,

        @NotNull(message = "消息 ID 不能为空")
        @Positive(message = "消息 ID 必须为正数")
        Long messageId
) {
}
