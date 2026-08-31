package com.chatroom.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChannelUpdateRequest(
        @Size(max = 100, message = "频道名称不能超过 100 个字符")
        @Pattern(regexp = "(?s).*\\S.*", message = "频道名称不能为空白")
        String name,

        @Size(max = 255, message = "频道描述不能超过 255 个字符")
        String description
) {
    @AssertTrue(message = "至少需要提供一个可更新字段")
    public boolean isAnyFieldPresent() {
        return name != null || description != null;
    }
}
