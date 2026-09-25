package com.chatroom.enums;

/**
 * 用户状态。对应 users.status，这是数据库里的持久化设置；
 * Redis 里的 presence:{userId} 才是"当前是否在线"的实时记录（TTL 300 秒）。
 * INVISIBLE 对外一律呈现为 OFFLINE，转换发生在 PresenceService，不在本枚举。
 */
public enum UserStatus {
    ONLINE, OFFLINE, INVISIBLE
}
