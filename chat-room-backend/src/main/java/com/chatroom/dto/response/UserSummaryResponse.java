package com.chatroom.dto.response;

/**
 * 用户摘要，用于在聊天界面中标识一个人（频道创建者、消息发送者、私聊对方等）。
 */
public record UserSummaryResponse(
        /** 用户 ID */
        Long id,
        /** 登录名，也是 @提及的匹配依据 */
        String username,
        /** 昵称 */
        String nickname,
        /** 头像地址 */
        String avatarUrl
) {
}
