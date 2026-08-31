package com.chatroom.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatTypingRequest(
        @NotNull(message = "频道 ID 不能为空")
        @Positive(message = "频道 ID 必须为正数")
        Long channelId,

        @NotNull(message = "输入状态不能为空")
        Boolean typing
) {
}
