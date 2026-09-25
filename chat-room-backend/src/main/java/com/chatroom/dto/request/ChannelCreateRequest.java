package com.chatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 输入边界：只描述"客户端可以传什么、值的形状是否合法"。
// 故意没有 creatorId 字段——创建者由服务端认证身份（SecurityContextHolder）决定，
// 让客户端传就等于允许冒充任意用户建群。重名判断、权限判断都在 Service。
//
// isPublic 用包装类型 Boolean 而非 boolean：只有包装类型才能表达"没传这个字段"，
// 基本类型会把缺省值变成 false，导致"不传 isPublic"被误解为"建私密频道"。
//
// 校验注解的坑：@Size 对 null 是放行的，所以要"必填 + 长度限制"必须
// @NotBlank 与 @Size 一起写（本类 name 就是这样）。
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
