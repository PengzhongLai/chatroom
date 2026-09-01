package com.chatroom.service;

import com.chatroom.constants.RedisConstants;
import com.chatroom.entity.User;
import com.chatroom.enums.UserStatus;
import com.chatroom.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 在线状态管理服务。
 * 使用 Redis 维护在线用户集合，替代原进程内 ConcurrentHashMap：
 * <ul>
 *   <li>每个用户一个 String key（presence:{userId}），值存状态名，带 TTL 兜底清理；</li>
 *   <li>Set（presence:online）作为在线用户索引，避免 KEYS/SCAN 遍历；</li>
 *   <li>正常断开由 WebSocket 事件显式删除，异常断线（服务器宕机/网络分区）由 TTL 自愈；</li>
 *   <li>Set 中过期残留的成员在读取快照时惰性清理。</li>
 * </ul>
 * 连接时恢复数据库持久化状态，断开时广播 OFFLINE，手动切换时写数据库。
 * 隐身模式（INVISIBLE）在 Redis 中保留状态，对外广播为 OFFLINE。
 */
@Service
public class PresenceService {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceService(StringRedisTemplate stringRedisTemplate,
                           UserRepository userRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * WebSocket 连接时调用。恢复持久化状态，新用户默认 ONLINE
     */
    public void userConnected(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        UserStatus persisted = user.getStatus();
        // 隐身保持隐身，其余设为在线
        UserStatus newStatus = (persisted == UserStatus.INVISIBLE) ? UserStatus.INVISIBLE : UserStatus.ONLINE;
        saveStatus(userId, newStatus);

        // 仅在状态变化时同步数据库（如新用户从默认 OFFLINE → ONLINE）
        if (persisted != newStatus) {
            user.setStatus(newStatus);
            userRepository.save(user);
        }

        broadcastStatus(userId, visibleStatus(newStatus));
    }

    /**
     * WebSocket 断开时调用。从 Redis 删除状态并广播离线
     */
    public void userDisconnected(Long userId) {
        stringRedisTemplate.delete(RedisConstants.PRESENCE_KEY_PREFIX + userId);
        stringRedisTemplate.opsForSet().remove(RedisConstants.PRESENCE_ONLINE_SET, userId.toString());
        broadcastStatus(userId, UserStatus.OFFLINE);
    }

    /**
     * 手动切换在线状态（来自前端菜单）。持久化到数据库 + 写入 Redis
     */
    public void setStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setStatus(status);
        userRepository.save(user);

        saveStatus(userId, status);
        broadcastStatus(userId, visibleStatus(status));
    }

    /**
     * 写入 Redis：SET 状态 + 续期 TTL + 加入在线索引
     */
    private void saveStatus(Long userId, UserStatus status) {
        String key = RedisConstants.PRESENCE_KEY_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(key, status.name(), RedisConstants.PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForSet().add(RedisConstants.PRESENCE_ONLINE_SET, userId.toString());
    }

    /**
     * 心跳续期：刷新在线状态 TTL。前端每 60 秒调一次。
     * 极端情况（心跳中断超过 TTL）key 已过期，则按数据库持久化状态恢复。
     */
    public void renewPresence(Long userId) {
        String key = RedisConstants.PRESENCE_KEY_PREFIX + userId;
        Boolean renewed = stringRedisTemplate.expire(key, RedisConstants.PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(renewed)) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                saveStatus(userId, user.getStatus());
            }
        }
    }

    /**
     * INVISIBLE 转 OFFLINE，其余状态不变（隐身对外显示离线）
     */
    private UserStatus visibleStatus(UserStatus actual) {
        return actual == UserStatus.INVISIBLE ? UserStatus.OFFLINE : actual;
    }

    /**
     * 获取所有在线用户状态（供 HTTP 接口调用，WebSocket 连接者拉取全量快照）
     */
    public Map<Long, String> getAllStatuses() {
        Map<Long, String> result = new LinkedHashMap<>();
        Set<String> onlineIds = stringRedisTemplate.opsForSet().members(RedisConstants.PRESENCE_ONLINE_SET);
        if (onlineIds == null) return result;

        for (String idStr : onlineIds) {
            String status = stringRedisTemplate.opsForValue().get(RedisConstants.PRESENCE_KEY_PREFIX + idStr);
            if (status == null) {
                // TTL 已过期但 Set 成员残留 → 惰性清理
                stringRedisTemplate.opsForSet().remove(RedisConstants.PRESENCE_ONLINE_SET, idStr);
                continue;
            }
            result.put(Long.parseLong(idStr), visibleStatus(UserStatus.valueOf(status)).name());
        }
        return result;
    }

    private void broadcastStatus(Long userId, UserStatus status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("status", status.name());
        payload.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/presence", payload);
    }
}
