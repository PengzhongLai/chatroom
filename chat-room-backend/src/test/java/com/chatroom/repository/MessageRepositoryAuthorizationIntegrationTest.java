package com.chatroom.repository;

import com.chatroom.entity.Channel;
import com.chatroom.entity.ChannelMember;
import com.chatroom.entity.Message;
import com.chatroom.entity.PrivateChat;
import com.chatroom.entity.User;
import com.chatroom.enums.ChatStatus;
import com.chatroom.enums.HistoryLevel;
import com.chatroom.enums.MemberRole;
import com.chatroom.enums.MessageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MessageRepositoryAuthorizationIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private PrivateChatRepository privateChatRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Test
    void channelSearchIsVisibleToMemberButNotOutsider() {
        String keyword = uniqueKeyword();
        User creator = user("creator");
        User outsider = user("outsider");
        Channel channel = channel(creator);
        member(channel, creator, HistoryLevel.ALL, null, LocalDateTime.now());
        Message secret = message(channel, creator, keyword, LocalDateTime.now());

        assertThat(searchIds(creator.getId(), keyword)).containsExactly(secret.getId());
        assertThat(searchIds(outsider.getId(), keyword)).isEmpty();
    }

    @Test
    void channelSearchHonorsNoneAndLimitedHistory() {
        String keyword = uniqueKeyword();
        User creator = user("creator");
        User noneMember = user("none");
        User limitedMember = user("limited");
        Channel channel = channel(creator);
        member(channel, creator, HistoryLevel.ALL, null, LocalDateTime.now().minusDays(10));
        member(channel, noneMember, HistoryLevel.NONE, null, LocalDateTime.now());
        member(channel, limitedMember, HistoryLevel.LIMITED, 1, LocalDateTime.now().minusDays(10));
        Message older = message(channel, creator, keyword + "-older", LocalDateTime.now().minusDays(2));
        Message newer = message(channel, creator, keyword + "-newer", LocalDateTime.now().minusDays(1));

        assertThat(searchIds(noneMember.getId(), keyword)).isEmpty();
        assertThat(searchIds(limitedMember.getId(), keyword)).containsExactly(newer.getId());
        assertThat(searchIds(creator.getId(), keyword)).containsExactly(newer.getId(), older.getId());
    }

    @Test
    void privateSearchIsVisibleOnlyToParticipants() {
        String keyword = uniqueKeyword();
        User first = user("first");
        User second = user("second");
        User outsider = user("outsider");
        PrivateChat chat = new PrivateChat(first, second, first);
        chat.setStatus(ChatStatus.ACTIVE);
        chat = privateChatRepository.saveAndFlush(chat);

        Message secret = new Message();
        secret.setPrivateChat(chat);
        secret.setSender(first);
        secret.setType(MessageType.TEXT);
        secret.setContent(keyword);
        secret = messageRepository.saveAndFlush(secret);

        assertThat(searchIds(first.getId(), keyword)).containsExactly(secret.getId());
        assertThat(searchIds(second.getId(), keyword)).containsExactly(secret.getId());
        assertThat(searchIds(outsider.getId(), keyword)).isEmpty();
    }

    private User user(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User("stage1_" + label + "_" + suffix, "test-only-hash", label);
        return userRepository.saveAndFlush(user);
    }

    private Channel channel(User creator) {
        Channel channel = new Channel();
        channel.setName("stage1-" + UUID.randomUUID());
        channel.setDescription("transactional search authorization test");
        channel.setCreator(creator);
        channel.setIsPublic(false);
        channel.setIsMuted(false);
        return channelRepository.saveAndFlush(channel);
    }

    private ChannelMember member(Channel channel,
                                 User user,
                                 HistoryLevel level,
                                 Integer limit,
                                 LocalDateTime joinedAt) {
        ChannelMember member = new ChannelMember();
        member.setChannel(channel);
        member.setUser(user);
        member.setRole(channel.getCreator().getId().equals(user.getId())
                ? MemberRole.CREATOR
                : MemberRole.MEMBER);
        member.setHistoryLevel(level);
        member.setHistoryLimit(limit);
        member.setJoinedAt(joinedAt);
        return channelMemberRepository.saveAndFlush(member);
    }

    private Message message(Channel channel, User sender, String content, LocalDateTime createdAt) {
        Message message = new Message();
        message.setChannel(channel);
        message.setSender(sender);
        message.setType(MessageType.TEXT);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        return messageRepository.saveAndFlush(message);
    }

    private java.util.List<Long> searchIds(Long userId, String keyword) {
        return messageRepository.searchAccessible(userId, keyword, PageRequest.of(0, 20))
                .stream()
                .map(result -> result.getId())
                .toList();
    }

    private String uniqueKeyword() {
        return "stage1secret" + UUID.randomUUID().toString().replace("-", "");
    }
}
