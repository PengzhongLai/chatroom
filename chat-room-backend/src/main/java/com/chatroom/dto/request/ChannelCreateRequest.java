package com.chatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建频道的请求体。创建者由服务端从登录身份取得，所以这里没有 creatorId 字段。
 */
public record ChannelCreateRequest(
        /** 频道名称，必填，最长 100 字符 */
        @NotBlank(message = "频道名称不能为空")
        @Size(max = 100, message = "频道名称不能超过 100 个字符")
        String name,

        /** 频道描述，可选，最长 255 字符 */
        @Size(max = 255, message = "频道描述不能超过 255 个字符")
        String description,

        /** 是否公开频道，可不传。用包装类型才能区分"没传"和"传了 false" */
        Boolean isPublic
) {
    /** 未传 isPublic 时按公开频道处理 */
    public boolean resolvedIsPublic() {
        return isPublic == null || isPublic;
    }
}
