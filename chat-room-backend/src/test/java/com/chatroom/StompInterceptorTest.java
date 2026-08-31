package com.chatroom;

import com.chatroom.entity.Channel;
import com.chatroom.entity.Message;
import com.chatroom.entity.PrivateChat;
import com.chatroom.entity.User;
import com.chatroom.repository.ChannelMemberRepository;
import com.chatroom.repository.MessageRepository;
import com.chatroom.repository.PrivateChatRepository;
import com.chatroom.repository.UserRepository;
import com.chatroom.security.JwtTokenProvider;
import com.chatroom.websocket.StompInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChannelMemberRepository channelMemberRepository;
    @Mock
    private PrivateChatRepository privateChatRepository;
    @Mock
    private MessageRepository messageRepository;

    private StompInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompInterceptor(
                jwtTokenProvider,
                userRepository,
                channelMemberRepository,
                privateChatRepository,
                messageRepository,
                new ObjectMapper()
        );
    }

    @Test
    void connectWithoutTokenIsRejected() {
        var message = stompMessage(StompCommand.CONNECT, null, null, "");

        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connectWithInvalidOrExpiredTokenIsRejected() {
        when(jwtTokenProvider.validateToken("expired-token")).thenReturn(false);
        var message = connectMessage("Bearer expired-token");

        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connectWithValidTokenSetsUserIdPrincipal() {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(42L);
        when(userRepository.existsById(42L)).thenReturn(true);

        var result = interceptor.preSend(connectMessage("Bearer valid-token"), null);
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

        assertNotNull(accessor);
        assertNotNull(accessor.getUser());
        assertEquals("42", accessor.getUser().getName());
    }

    @Test
    void channelSubscriptionRequiresMembership() {
        when(channelMemberRepository.existsByChannel_IdAndUser_Id(7L, 42L)).thenReturn(false);
        var denied = stompMessage(
                StompCommand.SUBSCRIBE, "/topic/channel.7", principal(42L), "");

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(denied, null));

        when(channelMemberRepository.existsByChannel_IdAndUser_Id(7L, 42L)).thenReturn(true);
        var allowed = stompMessage(
                StompCommand.SUBSCRIBE, "/topic/channel.7.typing", principal(42L), "");

        assertDoesNotThrow(() -> interceptor.preSend(allowed, null));
    }

    @Test
    void onlyOwnUserQueueSyntaxIsAllowed() {
        var ownQueue = stompMessage(
                StompCommand.SUBSCRIBE, "/user/queue/private", principal(42L), "");
        var anotherUsersQueue = stompMessage(
                StompCommand.SUBSCRIBE, "/user/99/queue/private", principal(42L), "");
        var directQueue = stompMessage(
                StompCommand.SUBSCRIBE, "/queue/private", principal(42L), "");

        assertDoesNotThrow(() -> interceptor.preSend(ownQueue, null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(anotherUsersQueue, null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(directQueue, null));
    }

    @Test
    void clientCannotPublishDirectlyToBrokerDestination() {
        var message = stompMessage(
                StompCommand.SEND,
                "/topic/channel.7",
                principal(42L),
                "{\"channelId\":7,\"content\":\"spoofed\"}"
        );

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void channelSendRequiresMembership() {
        var message = stompMessage(
                StompCommand.SEND,
                "/app/chat.send",
                principal(42L),
                "{\"channelId\":7,\"content\":\"hello\"}"
        );
        when(channelMemberRepository.existsByChannel_IdAndUser_Id(7L, 42L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));

        when(channelMemberRepository.existsByChannel_IdAndUser_Id(7L, 42L)).thenReturn(true);
        assertDoesNotThrow(() -> interceptor.preSend(message, null));
    }

    @Test
    void readReceiptMustReferenceAMessageInTheAuthorizedChannel() {
        Channel channel = new Channel();
        channel.setId(7L);
        Message storedMessage = new Message();
        storedMessage.setChannel(channel);
        when(messageRepository.findById(88L)).thenReturn(Optional.of(storedMessage));
        when(channelMemberRepository.existsByChannel_IdAndUser_Id(7L, 42L)).thenReturn(true);

        var allowed = stompMessage(
                StompCommand.SEND,
                "/app/chat.read",
                principal(42L),
                "{\"channelId\":7,\"messageId\":88}"
        );
        var mismatchedChannel = stompMessage(
                StompCommand.SEND,
                "/app/chat.read",
                principal(42L),
                "{\"channelId\":8,\"messageId\":88}"
        );

        assertDoesNotThrow(() -> interceptor.preSend(allowed, null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(mismatchedChannel, null));
    }

    @Test
    void privateSendRequiresParticipant() {
        PrivateChat chat = new PrivateChat();
        chat.setUser1(user(10L));
        chat.setUser2(user(20L));
        when(privateChatRepository.findById(9L)).thenReturn(Optional.of(chat));

        var participantSend = stompMessage(
                StompCommand.SEND,
                "/app/private.send",
                principal(10L),
                "{\"chatId\":9,\"content\":\"hello\"}"
        );
        var outsiderSend = stompMessage(
                StompCommand.SEND,
                "/app/private.send",
                principal(30L),
                "{\"chatId\":9,\"content\":\"hello\"}"
        );

        assertDoesNotThrow(() -> interceptor.preSend(participantSend, null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(outsiderSend, null));
    }

    @Test
    void sendAndSubscribeWithoutAuthenticatedPrincipalAreRejected() {
        var send = stompMessage(
                StompCommand.SEND, "/app/chat.send", null, "{\"channelId\":7}");
        var subscribe = stompMessage(
                StompCommand.SUBSCRIBE, "/topic/presence", null, "");

        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(send, null));
        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(subscribe, null));
    }

    private org.springframework.messaging.Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private org.springframework.messaging.Message<byte[]> stompMessage(
            StompCommand command,
            String destination,
            Principal principal,
            String payload) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (principal != null) {
            accessor.setUser(principal);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(
                payload.getBytes(StandardCharsets.UTF_8),
                accessor.getMessageHeaders()
        );
    }

    private Principal principal(Long userId) {
        return () -> userId.toString();
    }

    private User user(Long userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }
}
