package com.chatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteCodeRequest(
        @NotBlank(message = "邀请码不能为空")
        @Size(max = 20, message = "邀请码不能超过 20 个字符")
        String inviteCode
) {
}
