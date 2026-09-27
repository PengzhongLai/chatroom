package com.chatroom.config;

import com.chatroom.websocket.StompInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * WebSocket / STOMP 配置：注册端点、定义目的地前缀、挂载入站拦截器。
 * 客户端发送的帧走 clientInboundChannel（拦截器在这里生效），
 * 服务端推送的帧走 clientOutboundChannel（不经过拦截器）。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 入站帧的认证与授权拦截器 */
    private final StompInterceptor stompInterceptor;
    /** 允许连接的前端来源，防跨站 WebSocket 劫持（CSWSH）；多个用逗号分隔 */
    private final String[] allowedOrigins;

    public WebSocketConfig(
            StompInterceptor stompInterceptor,
            @Value("${app.websocket.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.stompInterceptor = stompInterceptor;
        // 按逗号切分并去掉空白项；配置为 * 会让任意站点都能连，生产环境必须写真实域名
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    /** 定义三类目的地前缀：/topic 与 /queue 交给内存 Broker，/app 是客户端发送前缀 */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单消息代理，/topic 用于广播（频道消息、在线状态），/queue 用于点对点（私聊、错误）
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息的前缀，如 /app/chat.send
        registry.setApplicationDestinationPrefixes("/app");
        // 用户专属消息前缀，如 /user/queue/private 解析为对应用户的队列
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 注册传输端点 /ws，并开启 SockJS 包装。
     * 注意：SockJS 会在 WebSocket 外再套一层自己的帧协议（o / a[...] / c[...]），
     * 所以裸 WebSocket 直连 /ws 会被拒（400），必须先访问 /ws/info 协商传输方式。
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点，前端通过 SockJS 连接 http://localhost:8080/ws
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    /** 把拦截器挂到入站通道——这是它真正参与每个 CONNECT/SUBSCRIBE/SEND 帧的地方 */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 注册 STOMP 拦截器，在 CONNECT 帧中校验 JWT 并设置用户身份
        registration.interceptors(stompInterceptor);
    }
}
