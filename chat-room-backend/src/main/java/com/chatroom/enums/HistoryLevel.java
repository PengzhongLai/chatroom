package com.chatroom.enums;

/**
 * 新成员能看到多少历史消息。对应 channel_members.history_level。
 * NONE 靠 joined_at 做分界，LIMITED 靠 history_limit（为空时按 50 处理）。
 */
public enum HistoryLevel {
    NONE, LIMITED, ALL
}
