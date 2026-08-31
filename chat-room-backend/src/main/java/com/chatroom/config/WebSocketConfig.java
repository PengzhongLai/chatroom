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

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompInterceptor stompInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            StompInterceptor stompInterceptor,
            @Value("${app.websocket.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.stompInterceptor = stompInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单消息代理，/topic 用于广播（频道消息、在线状态），/queue 用于点对点（私聊、错误）
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息的前缀，如 /app/chat.send
        registry.setApplicationDestinationPrefixes("/app");
        // 用户专属消息前缀，如 /user/queue/private 解析为对应用户的队列
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点，前端通过 SockJS 连接 http://localhost:8080/ws
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 注册 STOMP 拦截器，在 CONNECT 帧中校验 JWT 并设置用户身份
        registration.interceptors(stompInterceptor);
    }
}
