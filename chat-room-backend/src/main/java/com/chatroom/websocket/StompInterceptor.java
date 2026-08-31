package com.chatroom.websocket;

import com.chatroom.entity.Channel;
import com.chatroom.entity.PrivateChat;
import com.chatroom.entity.User;
import com.chatroom.repository.ChannelMemberRepository;
import com.chatroom.repository.MessageRepository;
import com.chatroom.repository.PrivateChatRepository;
import com.chatroom.repository.UserRepository;
import com.chatroom.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 协议拦截器。
 * 在 CONNECT 阶段认证 JWT，并对客户端 SEND/SUBSCRIBE 目的地进行默认拒绝式授权。
 * 后续所有 @MessageMapping 方法通过 Principal 参数获取 userId。
 * 注意：WebSocket 消息处理线程没有 HTTP SecurityContext，必须通过 Principal 传参。
 */
@Component
public class StompInterceptor implements ChannelInterceptor {

    private static final Pattern CHANNEL_TOPIC =
            Pattern.compile("^/topic/channel\\.(\\d+)(?:\\.typing)?$");
    private static final Set<String> USER_SUBSCRIPTIONS = Set.of(
            "/user/queue/private",
            "/user/queue/errors"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final PrivateChatRepository privateChatRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public StompInterceptor(JwtTokenProvider jwtTokenProvider,
                            UserRepository userRepository,
                            ChannelMemberRepository channelMemberRepository,
                            PrivateChatRepository privateChatRepository,
                            MessageRepository messageRepository,
                            ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.privateChatRepository = privateChatRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticateConnect(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(message, accessor);
            default -> {
                // ACK/NACK/UNSUBSCRIBE/DISCONNECT do not create new access paths.
            }
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if (!StringUtils.hasText(token)) {
            throw new BadCredentialsException("WebSocket 认证失败");
        }

        try {
            if (!jwtTokenProvider.validateToken(token)) {
                throw new BadCredentialsException("WebSocket 认证失败");
            }
            Long userId = jwtTokenProvider.getUserId(token);
            if (userId == null || userId <= 0 || !userRepository.existsById(userId)) {
                throw new BadCredentialsException("WebSocket 认证失败");
            }
            accessor.setUser(new StompUserPrincipal(userId));
        } catch (BadCredentialsException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BadCredentialsException("WebSocket 认证失败", e);
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Long userId = authenticatedUserId(accessor);
        String destination = requiredDestination(accessor);

        Matcher channelMatcher = CHANNEL_TOPIC.matcher(destination);
        if (channelMatcher.matches()) {
            ensureChannelMember(positiveLong(channelMatcher.group(1)), userId);
            return;
        }

        if ("/topic/presence".equals(destination) || USER_SUBSCRIPTIONS.contains(destination)) {
            return;
        }

        // This also rejects /user/{otherUser}/queue/** and direct /queue/** subscriptions.
        throw denied();
    }

    private void authorizeSend(Message<?> message, StompHeaderAccessor accessor) {
        Long userId = authenticatedUserId(accessor);
        String destination = requiredDestination(accessor);

        // Clients may only enter through application handlers, never publish to broker/user destinations.
        if (!destination.startsWith("/app/")) {
            throw denied();
        }

        JsonNode payload = readPayload(message);
        switch (destination) {
            case "/app/chat.send", "/app/chat.typing" ->
                    ensureChannelMember(requiredLong(payload, "channelId"), userId);
            case "/app/chat.read" -> authorizeChannelRead(payload, userId);
            case "/app/chat.recall" -> authorizeChannelRecall(payload, userId);
            case "/app/private.send" -> authorizePrivateSend(payload, userId);
            default -> throw denied();
        }
    }

    private void authorizeChannelRead(JsonNode payload, Long userId) {
        Long channelId = requiredLong(payload, "channelId");
        Long messageId = requiredLong(payload, "messageId");
        com.chatroom.entity.Message storedMessage =
                messageRepository.findById(messageId).orElseThrow(this::denied);
        Channel messageChannel = storedMessage.getChannel();
        if (messageChannel == null || !channelId.equals(messageChannel.getId())) {
            throw denied();
        }
        ensureChannelMember(channelId, userId);
    }

    private void authorizeChannelRecall(JsonNode payload, Long userId) {
        Long messageId = requiredLong(payload, "messageId");
        com.chatroom.entity.Message storedMessage =
                messageRepository.findById(messageId).orElseThrow(this::denied);
        Channel messageChannel = storedMessage.getChannel();
        if (messageChannel == null) {
            throw denied();
        }
        ensureChannelMember(messageChannel.getId(), userId);
    }

    private void authorizePrivateSend(JsonNode payload, Long userId) {
        Long chatId = requiredLong(payload, "chatId");
        PrivateChat chat = privateChatRepository.findById(chatId).orElseThrow(this::denied);
        User user1 = chat.getUser1();
        User user2 = chat.getUser2();
        boolean participant = (user1 != null && userId.equals(user1.getId()))
                || (user2 != null && userId.equals(user2.getId()));
        if (!participant) {
            throw denied();
        }
    }

    private void ensureChannelMember(Long channelId, Long userId) {
        if (!channelMemberRepository.existsByChannel_IdAndUser_Id(channelId, userId)) {
            throw denied();
        }
    }

    private Long authenticatedUserId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new BadCredentialsException("WebSocket 会话未认证");
        }
        try {
            Long userId = Long.parseLong(principal.getName());
            if (userId <= 0) {
                throw new NumberFormatException("non-positive user id");
            }
            return userId;
        } catch (RuntimeException e) {
            throw new BadCredentialsException("WebSocket 会话身份无效", e);
        }
    }

    private String requiredDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw denied();
        }
        return destination;
    }

    private JsonNode readPayload(Message<?> message) {
        Object payload = message.getPayload();
        try {
            if (payload instanceof byte[] bytes) {
                return objectMapper.readTree(bytes);
            }
            if (payload instanceof String text) {
                return objectMapper.readTree(text);
            }
            return objectMapper.valueToTree(payload);
        } catch (Exception e) {
            throw denied();
        }
    }

    private Long requiredLong(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        if (value == null || value.isNull()) {
            throw denied();
        }
        try {
            return positiveLong(value.isIntegralNumber()
                    ? Long.toString(value.longValue())
                    : value.textValue());
        } catch (RuntimeException e) {
            throw denied();
        }
    }

    private Long positiveLong(String value) {
        try {
            long result = Long.parseLong(value);
            if (result <= 0) {
                throw new NumberFormatException("non-positive id");
            }
            return result;
        } catch (RuntimeException e) {
            throw denied();
        }
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("无权访问该 WebSocket 目的地");
    }

    /** 从 CONNECT 帧头中提取 Bearer token */
    private String extractToken(StompHeaderAccessor accessor) {
        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private record StompUserPrincipal(Long userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
}
