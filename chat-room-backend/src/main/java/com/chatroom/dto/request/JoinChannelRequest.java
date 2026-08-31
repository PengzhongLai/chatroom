package com.chatroom.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JoinChannelRequest(
        @Size(max = 20, message = "邀请码不能超过 20 个字符")
        @Pattern(regexp = "\\S+", message = "邀请码不能为空白")
        String inviteCode
) {
}
