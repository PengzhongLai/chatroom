package com.chatroom.enums;

/**
 * 新成员能看到多少历史消息。对应 channel_members.history_level。
 */
public enum HistoryLevel {
    NONE,      // 只能看到自己入群之后的消息，以 joined_at 为分界
    LIMITED,   // 只能看到最近 N 条，N 取 history_limit，未设置时按 50
    ALL        // 可以看到频道全部历史消息
}
