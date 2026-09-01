package com.chatroom.constants;

/**
 * Redis 键与 TTL 的统一管理。
 * 所有 Redis 相关常量集中定义，使用时通过 RedisConstants.XXX 引入，避免散落各 Service。
 */
public class RedisConstants {

    /** 在线状态 key：presence:{userId}，value = 状态名（ONLINE/INVISIBLE） */
    public static final String PRESENCE_KEY_PREFIX = "presence:";
    /** 在线用户索引 Set：presence:online，成员 = 在线 userId，避免 KEYS/SCAN 遍历 */
    public static final String PRESENCE_ONLINE_SET = "presence:online";
    /** 在线状态 TTL（秒）：正常断开靠 WebSocket 事件清理，异常断线靠 TTL 自愈 */
    public static final Long PRESENCE_TTL_SECONDS = 300L;
}
