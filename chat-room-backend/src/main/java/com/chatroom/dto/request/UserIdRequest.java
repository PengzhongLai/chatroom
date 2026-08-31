package com.chatroom.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UserIdRequest(
        @NotNull(message = "用户 ID 不能为空")
        @Positive(message = "用户 ID 必须为正数")
        Long userId
) {
}
