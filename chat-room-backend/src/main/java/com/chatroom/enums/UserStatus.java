package com.chatroom.enums;

/**
 * 用户状态。对应 users.status，是数据库里的持久化设置；
 * 实时在线状态另存在 Redis 的 presence:{userId}（TTL 300 秒）。
 */
public enum UserStatus {
    ONLINE,     // 在线
    OFFLINE,    // 离线
    INVISIBLE   // 隐身，对外一律显示为离线
}
