package com.chatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MemberActionRequest(
        @NotBlank(message = "成员操作不能为空")
        @Pattern(regexp = "kick", message = "成员操作仅支持 kick")
        String action
) {
}
