package com.chatroom.service;

import com.chatroom.entity.User;
import com.chatroom.enums.UserStatus;
import com.chatroom.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线状态管理服务。
 * 使用 ConcurrentHashMap 维护在线用户集合。
 * 连接时恢复持久化状态，断开时广播 OFFLINE，手动切换时写数据库。
 * 隐身模式（INVISIBLE）在内存中保留连接，对外广播为 OFFLINE。
 */
@Service
public class PresenceService {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /** userId → 当前有效状态（INVISIBLE 对外显示 OFFLINE） */
    private final Map<Long, UserStatus> onlineUsers = new ConcurrentHashMap<>();

    public PresenceService(UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /** WebSocket 连接时调用。恢复持久化状态，新用户默认 ONLINE */
    public void userConnected(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        UserStatus persisted = user.getStatus();
        // 隐身保持隐身，其余设为在线
        UserStatus newStatus = (persisted == UserStatus.INVISIBLE) ? UserStatus.INVISIBLE : UserStatus.ONLINE;
        onlineUsers.put(userId, newStatus);

        // 仅在状态变化时同步数据库（如新用户从默认 OFFLINE → ONLINE）
        if (persisted != newStatus) {
            user.setStatus(newStatus);
            userRepository.save(user);
        }

        broadcastStatus(userId, visibleStatus(newStatus));
    }

    /** WebSocket 断开时调用。从在线集合移除并广播离线 */
    public void userDisconnected(Long userId) {
        onlineUsers.remove(userId);
        broadcastStatus(userId, UserStatus.OFFLINE);
    }

    /** 手动切换在线状态（来自前端菜单）。持久化到数据库 */
    public void setStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setStatus(status);
        userRepository.save(user);

        if (status == UserStatus.INVISIBLE) {
            // 内部仍保持连接，但对外广播为离线
            onlineUsers.put(userId, UserStatus.INVISIBLE);
            broadcastStatus(userId, UserStatus.OFFLINE);
        } else {
            onlineUsers.put(userId, status);
            broadcastStatus(userId, status);
        }
    }

    /** INVISIBLE 转 OFFLINE，其余状态不变（隐身对外显示离线） */
    private UserStatus visibleStatus(UserStatus actual) {
        return actual == UserStatus.INVISIBLE ? UserStatus.OFFLINE : actual;
    }

    /** 获取所有在线用户状态（供 HTTP 接口调用，WebSocket 连接者拉取全量快照） */
    public Map<Long, String> getAllStatuses() {
        Map<Long, String> result = new LinkedHashMap<>();
        for (Map.Entry<Long, UserStatus> e : onlineUsers.entrySet()) {
            result.put(e.getKey(), visibleStatus(e.getValue()).name());
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
