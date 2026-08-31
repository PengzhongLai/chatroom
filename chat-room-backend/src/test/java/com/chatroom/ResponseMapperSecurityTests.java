package com.chatroom;

import com.chatroom.dto.response.ChannelDetailResponse;
import com.chatroom.dto.response.ChannelSummaryResponse;
import com.chatroom.dto.response.MessageResponse;
import com.chatroom.dto.response.PrivateChatResponse;
import com.chatroom.entity.Channel;
import com.chatroom.entity.Message;
import com.chatroom.entity.PrivateChat;
import com.chatroom.entity.User;
import com.chatroom.enums.ChatStatus;
import com.chatroom.enums.MessageType;
import com.chatroom.mapper.ChannelResponseMapper;
import com.chatroom.mapper.MessageResponseMapper;
import com.chatroom.mapper.PrivateChatResponseMapper;
import com.chatroom.mapper.UserResponseMapper;
import com.chatroom.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponseMapperSecurityTests {

    private ObjectMapper objectMapper;
    private UserResponseMapper userResponseMapper;
    private ChannelResponseMapper channelResponseMapper;
    private PrivateChatResponseMapper privateChatResponseMapper;
    private MessageResponseMapper messageResponseMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        userResponseMapper = new UserResponseMapper();
        channelResponseMapper = new ChannelResponseMapper(userResponseMapper);
        privateChatResponseMapper = new PrivateChatResponseMapper(userResponseMapper);

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        messageResponseMapper = new MessageResponseMapper(userRepository, userResponseMapper);
    }

    @Test
    void userEntityPasswordIsIgnoredAsDefenseInDepth() throws Exception {
        User user = user(1L, "alice", "Alice", "bcrypt-hash-must-never-leak");

        String json = objectMapper.writeValueAsString(user);

        assertThat(json).doesNotContain("password", "bcrypt-hash-must-never-leak");
    }

    @Test
    void channelSummaryNeverContainsInviteCodeButAuthorizedDetailCan() throws Exception {
        User creator = user(1L, "owner", "Owner", "owner-password-hash");
        Channel channel = new Channel();
        channel.setId(9L);
        channel.setName("private-room");
        channel.setDescription("private");
        channel.setCreator(creator);
        channel.setIsPublic(false);
        channel.setInviteCode("secret-invite");
        channel.setIsMuted(false);

        ChannelSummaryResponse summary = channelResponseMapper.toSummary(channel);
        ChannelDetailResponse detail = channelResponseMapper.toDetail(channel);
        String summaryJson = objectMapper.writeValueAsString(summary);
        String detailJson = objectMapper.writeValueAsString(detail);

        assertThat(summaryJson).doesNotContain("inviteCode", "secret-invite", "password");
        assertThat(detailJson).contains("\"inviteCode\":\"secret-invite\"");
        assertThat(detailJson).doesNotContain("password", "owner-password-hash");
    }

    @Test
    void privateChatResponseContainsOnlyOtherUserView() throws Exception {
        User viewer = user(1L, "viewer", "Viewer", "viewer-password");
        User other = user(2L, "other", "Other", "other-password");
        PrivateChat chat = new PrivateChat(viewer, other, viewer);
        chat.setId(7L);
        chat.setStatus(ChatStatus.PENDING);

        PrivateChatResponse response = privateChatResponseMapper.toResponse(chat, viewer);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("otherUser").get("id").asLong()).isEqualTo(2L);
        assertThat(json.has("user1")).isFalse();
        assertThat(json.has("user2")).isFalse();
        assertThat(json.has("initiator")).isFalse();
        assertThat(json.toString()).doesNotContain("password", "viewer-password", "other-password");
    }

    @Test
    void recalledMessageNeverExposesOriginalTextOrFileMetadata() throws Exception {
        User sender = user(1L, "alice", "Alice", "sender-password");
        Channel channel = new Channel();
        channel.setId(3L);

        Message message = new Message();
        message.setId(42L);
        message.setChannel(channel);
        message.setSender(sender);
        message.setType(MessageType.FILE);
        message.setContent("original secret text");
        message.setFileName("secret-contract.pdf");
        message.setFilePath("/files/private-object");
        message.setIsRecalled(true);

        MessageResponse response = messageResponseMapper.toResponse(message);
        Map<String, Object> payload = messageResponseMapper.toPayload(message);
        String json = objectMapper.writeValueAsString(payload);

        assertThat(response.type()).isEqualTo(MessageType.SYSTEM);
        assertThat(response.content()).isNull();
        assertThat(response.fileName()).isNull();
        assertThat(response.filePath()).isNull();
        assertThat(response.mentions()).isEmpty();
        assertThat(json).doesNotContain(
                "original secret text",
                "secret-contract.pdf",
                "/files/private-object",
                "sender-password"
        );
    }

    private User user(Long id, String username, String nickname, String password) {
        User user = new User(username, password, nickname);
        user.setId(id);
        return user;
    }
}
