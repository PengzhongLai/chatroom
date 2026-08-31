package com.chatroom.service;

import com.chatroom.dto.response.MessageResponse;
import com.chatroom.entity.*;
import com.chatroom.enums.HistoryLevel;
import com.chatroom.enums.MessageType;
import com.chatroom.exception.BusinessException;
import com.chatroom.mapper.MessageResponseMapper;
import com.chatroom.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chatroom.validation.PaginationPolicy;

import java.util.*;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageReadRepository messageReadRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageResponseMapper messageResponseMapper;

    public MessageService(MessageRepository messageRepository,
                          MessageReadRepository messageReadRepository,
                          ChannelRepository channelRepository,
                          ChannelMemberRepository memberRepository,
                          UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate,
                          MessageResponseMapper messageResponseMapper) {
        this.messageRepository = messageRepository;
        this.messageReadRepository = messageReadRepository;
        this.channelRepository = channelRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.messageResponseMapper = messageResponseMapper;
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Long userId)) {
            throw BusinessException.unauthorized("用户未登录");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.unauthorized("登录用户不存在"));
    }

    private void ensureMember(Channel channel, User user) {
        if (!memberRepository.existsByChannelAndUser(channel, user)) {
            throw BusinessException.forbidden("你不是该频道的成员");
        }
    }

    /**
     * Send a message (WebSocket). Uses explicit userId from STOMP Principal.
     */
    @Transactional
    public Map<String, Object> sendMessage(Long userId, Long channelId, String content, MessageType type,
                                            String fileName, String filePath) {
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureMember(channel, sender);
        if (type == MessageType.SYSTEM) {
            throw BusinessException.badRequest("客户端不能发送系统消息");
        }

        // Check if channel is muted (only admins can bypass)
        if (channel.getIsMuted()) {
            ChannelMember member = memberRepository.findByChannelAndUser(channel, sender)
                    .orElseThrow(() -> BusinessException.conflict("频道成员数据不一致"));
            boolean isCreator = channel.getCreator() != null
                    && channel.getCreator().getId().equals(sender.getId());
            if (!isCreator && member.getRole() != com.chatroom.enums.MemberRole.ADMIN) {
                // Don't throw — send error back to the specific user via WebSocket
                Map<String, Object> errorPayload = new HashMap<>();
                errorPayload.put("type", "ERROR");
                errorPayload.put("message", "频道已被禁言");
                messagingTemplate.convertAndSendToUser(
                        userId.toString(), "/queue/errors", errorPayload);
                return Collections.emptyMap();
            }
        }

        Message message = new Message();
        message.setChannel(channel);
        message.setSender(sender);
        message.setContent(content);
        message.setType(type != null ? type : MessageType.TEXT);
        message.setFileName(fileName);
        message.setFilePath(filePath);
        message = messageRepository.save(message);

        Map<String, Object> payload = buildMessagePayload(message);
        messagingTemplate.convertAndSend("/topic/channel." + channelId, payload);

        return payload;
    }


    /** Mark a message as read by a specific user. */
    @Transactional
    public void markAsRead(Long userId, Long messageId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return;
        if (messageReadRepository.findByMessageAndUser(message, user).isEmpty()) {
            MessageRead mr = new MessageRead(message, user);
            messageReadRepository.save(mr);
        }
    }

    /**
     * Get paginated message history (HTTP). Uses SecurityContext for auth.
     * Returns messages in ASC order (oldest first) for frontend display.
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long channelId, int page, int size) {
        PaginationPolicy.validate(page, size);
        User user = currentUser();
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureMember(channel, user);

        ChannelMember member = memberRepository.findByChannelAndUser(channel, user)
                .orElseThrow(() -> BusinessException.conflict("频道成员数据不一致"));
        HistoryLevel level = member.getHistoryLevel();
        Page<Message> messagePage;

        switch (level) {
            case NONE -> {
                // Only messages after the member joined
                messagePage = messageRepository.findByChannelAndCreatedAtAfterOrderByCreatedAtDesc(
                        channel, member.getJoinedAt(), PageRequest.of(page, size));
            }
            case LIMITED -> {
                // Limited to most recent N messages
                int limit = member.getHistoryLimit() != null ? member.getHistoryLimit() : 50;
                int cappedSize = Math.min(size, limit - page * size);
                if (cappedSize <= 0) {
                    return Collections.emptyList();
                }
                messagePage = messageRepository.findByChannelOrderByCreatedAtDesc(
                        channel, PageRequest.of(page, cappedSize));
            }
            default -> {
                // ALL
                messagePage = messageRepository.findByChannelOrderByCreatedAtDesc(
                        channel, PageRequest.of(page, size));
            }
        }

        // Convert to payload list, reverse to ASC order for display
        List<MessageResponse> messages = new ArrayList<>();
        List<Message> content = messagePage.getContent();
        for (int i = content.size() - 1; i >= 0; i--) {
            messages.add(messageResponseMapper.toResponse(content.get(i)));
        }
        return messages;
    }

    /**
     * Recall a message (WebSocket). Uses explicit userId from STOMP Principal.
     */
    @Transactional
    public void recallMessage(Long userId, Long messageId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> BusinessException.notFound("消息不存在"));

        if (!message.getSender().getId().equals(user.getId())) {
            throw BusinessException.forbidden("只能撤回自己发送的消息");
        }

        message.setIsRecalled(true);
        messageRepository.save(message);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "RECALL");
        payload.put("messageId", messageId);

        if (message.getChannel() != null) {
            messagingTemplate.convertAndSend("/topic/channel." + message.getChannel().getId(), payload);
        }
    }

    /**
     * Recall a message (HTTP). Uses SecurityContext for auth.
     */
    @Transactional
    public void recallMessage(Long messageId) {
        User user = currentUser();
        recallMessage(user.getId(), messageId);
    }

    @Transactional
    public void recallChannelMessage(Long channelId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> BusinessException.notFound("消息不存在"));
        if (message.getChannel() == null || !channelId.equals(message.getChannel().getId())) {
            throw BusinessException.notFound("该频道中不存在此消息");
        }
        User user = currentUser();
        recallMessage(user.getId(), messageId);
    }

    /**
     * Broadcast typing notification (WebSocket). Uses explicit userId from STOMP Principal.
     */
    public void sendTypingNotification(Long userId, Long channelId, boolean typing) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("channelId", channelId);
        payload.put("userId", user.getId());
        payload.put("nickname", user.getNickname());
        payload.put("typing", typing);
        messagingTemplate.convertAndSend("/topic/channel." + channelId + ".typing", payload);
    }

    /** Build a JSON-friendly payload from a Message entity, including @mentions list. */
    public Map<String, Object> buildMessagePayload(Message message) {
        return messageResponseMapper.toPayload(message);
    }
}
