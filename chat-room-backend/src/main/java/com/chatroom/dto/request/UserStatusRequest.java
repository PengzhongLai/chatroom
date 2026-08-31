package com.chatroom.dto.request;

import com.chatroom.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
        @NotNull(message = "用户状态不能为空")
        UserStatus status
) {
}
