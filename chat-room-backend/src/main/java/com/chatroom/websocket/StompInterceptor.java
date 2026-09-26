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
 * STOMP 协议拦截器：WebSocket 通道的认证与授权。
 *
 * 三个命令各有分工：
 * - CONNECT：校验 JWT，成功后把 userId 绑到这条会话上（accessor.setUser）
 * - SUBSCRIBE：白名单式授权，订阅频道主题还需是该频道成员
 * - SEND：只允许发往 /app/**，再按具体目的地校验权限；未列出的目的地一律拒绝
 *
 * 与 HTTP 通道的关键差别：JWT 只在 CONNECT 时验证一次，之后的帧不再验 token。
 * 后续所有 @MessageMapping 方法通过 Principal 参数获取 userId。
 * 注意：WebSocket 消息处理线程没有 HTTP SecurityContext，必须通过 Principal 传参。
 */
@Component
public class StompInterceptor implements ChannelInterceptor {

    /** 允许订阅的频道地址形如 /topic/channel.10 或 /topic/channel.10.typing */
    private static final Pattern CHANNEL_TOPIC =
            Pattern.compile("^/topic/channel\\.(\\d+)(?:\\.typing)?$");
    /** 允许订阅的个人队列白名单。精确匹配，所以 /user/{别人}/queue/** 会被拒 */
    private static final Set<String> USER_SUBSCRIPTIONS = Set.of(
            "/user/queue/private",
            "/user/queue/errors"
    );

    /** 校验与解析 JWT */
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    /** 订阅频道、发送频道消息时校验成员身份 */
    private final ChannelMemberRepository channelMemberRepository;
    /** 发送私聊消息时校验参与者身份 */
    private final PrivateChatRepository privateChatRepository;
    /** 已读、撤回时反查消息所属频道 */
    private final MessageRepository messageRepository;
    /** 解析 SEND 帧的 JSON 载荷，取出 channelId / chatId / messageId */
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

    /** 每条 STOMP 消息进入通道前都会经过这里。抛异常即拒绝该帧 */
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

    /**
     * CONNECT 帧处理：校验 JWT 并把身份绑定到会话。
     * 三步都通过才 setUser：验签与过期、取出 userId、确认用户仍存在。
     * 绑定之后，该会话每条消息的 Principal 都是这个 userId。
     */
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

    /**
     * SUBSCRIBE 帧授权。只放行三类地址：
     * ① /topic/channel.{id}（含 .typing）——须是该频道成员
     * ② /topic/presence——在线状态广播
     * ③ /user/queue/private、/user/queue/errors——本人的个人队列
     * 其余一律拒绝（默认拒绝）。
     */
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

    /**
     * SEND 帧授权。先要求目的地以 /app/ 开头——客户端只能进应用处理器，
     * 不允许直接发布到 /topic 或 /queue，否则可绕过全部业务校验伪造广播。
     * 再按具体目的地分派到各自的权限检查，未列出的目的地一律拒绝。
     */
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

    /**
     * 已读回执授权。除成员身份外，还要求 messageId 指向的消息确实属于声明的 channelId。
     * 否则成员可以用自己频道的身份，去给别的频道的消息标记已读。
     */
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

    /**
     * 撤回授权。撤回请求不带 channelId，所以从数据库里的消息反查所属频道，
     * 再校验发送者是否该频道成员。是否本人发送由业务层判断。
     */
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

    /**
     * 私聊发送授权：当前用户必须是该会话的参与者之一。
     * 会话是否 ACTIVE 由 PrivateChatService 在业务层校验。
     */
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

    /** 校验用户是否该频道成员，不是则拒绝 */
    private void ensureChannelMember(Long channelId, Long userId) {
        if (!channelMemberRepository.existsByChannel_IdAndUser_Id(channelId, userId)) {
            throw denied();
        }
    }

    /** 从会话 Principal 取 userId。Principal 为空说明未认证（如跳过了 CONNECT） */
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

    /** 取帧的目的地，缺失则视为无权访问 */
    private String requiredDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw denied();
        }
        return destination;
    }

    /** 把 SEND 帧的载荷解析成 JSON。解析失败视为无权访问 */
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

    /** 从载荷中取一个正整数 ID 字段，缺失或非法则拒绝 */
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

    /** 把字符串转成正整数，非数字或非正数则拒绝 */
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

    /** 构造统一的拒绝异常。这是 STOMP 层的错误，不会变成 HTTP 403 */
    private AccessDeniedException denied() {
        return new AccessDeniedException("无权访问该 WebSocket 目的地");
    }

    /** 从 CONNECT 帧的 native header 提取 Bearer token（注意不是 HTTP 头） */
    private String extractToken(StompHeaderAccessor accessor) {
        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    /**
     * 绑定到 STOMP 会话的身份。只携带 userId，
     * getName() 返回其字符串形式，Controller 里再解析回 Long。
     */
    private record StompUserPrincipal(Long userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
}
