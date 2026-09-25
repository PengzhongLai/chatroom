package com.chatroom.dto.response;

// 全系统的"用户信息公共契约"：频道创建者、消息发送者、私聊对方、搜索结果都用它。
// 只保留"在聊天界面里认出一个人"的最小集合——username 还是 @提及的匹配依据。
// 被引用得越广越要克制：个别场景需要更多字段（如在线状态）应另开 DTO，
// 不要往这里加，否则所有出口一起放宽。
public record UserSummaryResponse(
        Long id,
        String username,
        String nickname,
        String avatarUrl
) {
}
