package com.chatroom.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PrivateChatCreateRequest(
        @NotNull(message = "目标用户 ID 不能为空")
        @Positive(message = "目标用户 ID 必须为正数")
        Long targetUserId
) {
}
