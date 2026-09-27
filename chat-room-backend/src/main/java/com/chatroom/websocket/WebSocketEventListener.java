package com.chatroom.websocket;

import com.chatroom.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * WebSocket 会话事件监听器：连接建立/断开时维护在线状态。
 *
 * 与 StompInterceptor 的分工：拦截器处理"帧"（CONNECT/SUBSCRIBE/SEND 的授权），
 * 本类处理"会话生命周期事件"（Spring 在会话建立/销毁时发布，不是客户端发的帧）。
 *
 * 注意：只有通过 CONNECT 认证的会话才有 Principal，所以 user 为 null 时说明
 * 这是一个未认证的会话，直接跳过，不写在线状态。
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);
    /** 在线状态写入 Redis（presence:{userId} + presence:online 集合） */
    private final PresenceService presenceService;

    public WebSocketEventListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    /** 会话连接成功：把用户标记为在线并广播状态变化 */
    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user != null) {
            Long userId = Long.parseLong(user.getName());     // Principal 里装的是 userId 字符串
            log.info("WebSocket connected: userId={}", userId);
            presenceService.userConnected(userId);
        }
    }

    /**
     * 会话断开：把用户标记为离线并广播。
     * 已知边界：同一用户开多个标签页时共享同一个 presence key，
     * 任意一个连接断开都会删除该 key，导致仍有其他连接时被误报为离线。
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user != null) {
            Long userId = Long.parseLong(user.getName());
            log.info("WebSocket disconnected: userId={}", userId);
            presenceService.userDisconnected(userId);
        }
    }
}
