package com.chatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChannelCreateRequest(
        @NotBlank(message = "频道名称不能为空")
        @Size(max = 100, message = "频道名称不能超过 100 个字符")
        String name,

        @Size(max = 255, message = "频道描述不能超过 255 个字符")
        String description,

        Boolean isPublic
) {
    public boolean resolvedIsPublic() {
        return isPublic == null || isPublic;
    }
}
