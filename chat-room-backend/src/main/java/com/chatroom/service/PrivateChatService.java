package com.chatroom.service;

import com.chatroom.dto.response.MessageResponse;
import com.chatroom.dto.response.PrivateChatResponse;
import com.chatroom.entity.*;
import com.chatroom.enums.ChatStatus;
import com.chatroom.enums.MessageType;
import com.chatroom.mapper.MessageResponseMapper;
import com.chatroom.mapper.PrivateChatResponseMapper;
import com.chatroom.exception.BusinessException;
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
public class PrivateChatService {

    private final PrivateChatRepository privateChatRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final MessageResponseMapper messageResponseMapper;
    private final PrivateChatResponseMapper privateChatResponseMapper;

    public PrivateChatService(PrivateChatRepository privateChatRepository,
                              UserRepository userRepository,
                              MessageRepository messageRepository,
                              SimpMessagingTemplate messagingTemplate,
                              MessageService messageService,
                              MessageResponseMapper messageResponseMapper,
                              PrivateChatResponseMapper privateChatResponseMapper) {
        this.privateChatRepository = privateChatRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
        this.messageService = messageService;
        this.messageResponseMapper = messageResponseMapper;
        this.privateChatResponseMapper = privateChatResponseMapper;
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

    /**
     * 发起私人聊天请求。如果对方用户已经对我有一个待处理请求，则自动激活。
     * 如果之前被拒绝，则重新请求为待处理状态。
     */
    @Transactional
    public PrivateChat initiateChat(Long targetUserId) {
        User me = currentUser();
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        if (me.getId().equals(target.getId())) {
            throw BusinessException.badRequest("不能和自己私聊");
        }

        // User order
        User user1 = me.getId() < target.getId() ? me : target;
        User user2 = me.getId() < target.getId() ? target : me;

        Optional<PrivateChat> existing = privateChatRepository.findBetweenUsers(user1, user2);
        if (existing.isPresent()) {
            PrivateChat chat = existing.get();
            if (chat.getStatus() == ChatStatus.ACTIVE) {
                return chat; // Already active, nothing to do
            }
            if (chat.getStatus() == ChatStatus.PENDING) {
                // If the other person initiated, auto-activate (mutual consent)
                if (!chat.getInitiator().getId().equals(me.getId())) {
                    chat.setStatus(ChatStatus.ACTIVE);
                    chat = privateChatRepository.save(chat);
                    notifyStatusChange(chat, ChatStatus.ACTIVE);
                    return chat;
                }
                // I initiated — already waiting
                return chat;
            }
            // REJECTED or DELETED — re-request
            chat.setInitiator(me);
            chat.setStatus(ChatStatus.PENDING);
            chat = privateChatRepository.save(chat);
            // Notify target
            notifyInvitation(chat, target);
            return chat;
        }

        // New chat
        PrivateChat chat = new PrivateChat(user1, user2, me);
        chat.setStatus(ChatStatus.PENDING);
        chat = privateChatRepository.save(chat);
        notifyInvitation(chat, target);
        return chat;
    }

    /** 接受待处理的私人聊天请求 */
    @Transactional
    public PrivateChat acceptChat(Long chatId) {
        User me = currentUser();
        PrivateChat chat = privateChatRepository.findById(chatId)
                .orElseThrow(() -> BusinessException.notFound("私聊不存在"));
        if (!isParticipant(chat, me)) throw BusinessException.forbidden("你不是参与者");
        if (chat.getStatus() != ChatStatus.PENDING) throw BusinessException.conflict("当前状态不允许此操作");
        if (chat.getInitiator().getId().equals(me.getId())) throw BusinessException.forbidden("不能同意自己的申请");

        chat.setStatus(ChatStatus.ACTIVE);
        chat = privateChatRepository.save(chat);
        notifyStatusChange(chat, ChatStatus.ACTIVE);
        return chat;
    }

    /** 拒绝一个待处理的私人聊天请求。 */
    @Transactional
    public void rejectChat(Long chatId) {
        User me = currentUser();
        PrivateChat chat = privateChatRepository.findById(chatId)
                .orElseThrow(() -> BusinessException.notFound("私聊不存在"));
        if (!isParticipant(chat, me)) throw BusinessException.forbidden("你不是参与者");
        if (chat.getStatus() != ChatStatus.PENDING) throw BusinessException.conflict("当前状态不允许此操作");
        if (chat.getInitiator().getId().equals(me.getId())) throw BusinessException.forbidden("不能拒绝自己的申请");

        chat.setStatus(ChatStatus.REJECTED);
        privateChatRepository.save(chat);
        // Notify initiator
        Map<String, Object> notify = buildChatPayload(chat, chat.getInitiator());
        notify.put("type", "REJECTED");
        messagingTemplate.convertAndSendToUser(
                chat.getInitiator().getId().toString(), "/queue/private", notify);
    }

    /** 删除双方面的私人聊天，包括所有消息。 */
    @Transactional
    public void deleteChat(Long chatId) {
        User me = currentUser();
        PrivateChat chat = privateChatRepository.findById(chatId)
                .orElseThrow(() -> BusinessException.notFound("私聊不存在"));
        if (!isParticipant(chat, me)) throw BusinessException.forbidden("你不是参与者");

        messageRepository.deleteByPrivateChat(chat);
        chat.setStatus(ChatStatus.DELETED);
        privateChatRepository.save(chat);
        // Notify the other person
        User other = chat.getUser1().getId().equals(me.getId()) ? chat.getUser2() : chat.getUser1();
        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("id", chatId);
        notify.put("type", "DELETED");
        messagingTemplate.convertAndSendToUser(
                other.getId().toString(), "/queue/private", notify);
    }

    /**
     * 发送私人消息。通过 /user/queue/private 推送给两个用户。
     */
    @Transactional
    public Map<String, Object> sendMessage(Long chatId, Long senderId, String content, MessageType type,
                                           String fileName, String filePath) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        PrivateChat chat = privateChatRepository.findById(chatId)
                .orElseThrow(() -> BusinessException.notFound("私聊不存在"));

        // Verify sender is a participant
        if (!isParticipant(chat, sender)) {
            throw BusinessException.forbidden("你不是该私聊的参与者");
        }
        if (type == MessageType.SYSTEM) {
            throw BusinessException.badRequest("客户端不能发送系统消息");
        }

        // Must be ACTIVE to send
        if (chat.getStatus() != ChatStatus.ACTIVE) {
            Map<String, Object> errorPayload = new HashMap<>();
            errorPayload.put("type", "ERROR");
            errorPayload.put("message", "私聊尚未激活");
            messagingTemplate.convertAndSendToUser(
                    senderId.toString(), "/queue/private", errorPayload);
            return Collections.emptyMap();
        }

        // Save message
        Message message = new Message();
        message.setPrivateChat(chat);
        message.setSender(sender);
        message.setContent(content);
        message.setType(type != null ? type : MessageType.TEXT);
        message.setFileName(fileName);
        message.setFilePath(filePath);
        message = messageRepository.save(message);

        Map<String, Object> payload = messageService.buildMessagePayload(message);
        payload.put("chatId", chatId);

        // Push to both users
        messagingTemplate.convertAndSendToUser(
                chat.getUser1().getId().toString(), "/queue/private", payload);
        messagingTemplate.convertAndSendToUser(
                chat.getUser2().getId().toString(), "/queue/private", payload);

        return payload;
    }

    /**
     * 获取当前用户的所有私聊及最后一条消息预览。
     */
    @Transactional(readOnly = true)
    public List<PrivateChatResponse> getChats() {
        User me = currentUser();
        List<PrivateChat> chats = privateChatRepository.findByUser(me);
        List<PrivateChatResponse> result = new ArrayList<>();

        for (PrivateChat chat : chats) {
            // Skip REJECTED and DELETED
            if (chat.getStatus() == ChatStatus.REJECTED || chat.getStatus() == ChatStatus.DELETED) continue;

            // Last message preview (only for ACTIVE chats)
            Message lastMessage = null;
            if (chat.getStatus() == ChatStatus.ACTIVE) {
                Page<Message> lastMsg = messageRepository.findByPrivateChatOrderByCreatedAtDesc(
                        chat, PageRequest.of(0, 1));
                if (!lastMsg.isEmpty()) {
                    lastMessage = lastMsg.getContent().get(0);
                }
            }

            result.add(privateChatResponseMapper.toResponse(chat, me, lastMessage));
        }
        return result;
    }

    /**
     * 获取私人聊天的分页消息历史记录（前端使用升序排列）。
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long chatId, int page, int size) {
        PaginationPolicy.validate(page, size);
        User me = currentUser();
        PrivateChat chat = privateChatRepository.findById(chatId)
                .orElseThrow(() -> BusinessException.notFound("私聊不存在"));

        if (!chat.getUser1().getId().equals(me.getId()) && !chat.getUser2().getId().equals(me.getId())) {
            throw BusinessException.forbidden("你不是该私聊的参与者");
        }

        Page<Message> messagePage = messageRepository.findByPrivateChatOrderByCreatedAtDesc(
                chat, PageRequest.of(page, size));

        List<MessageResponse> messages = new ArrayList<>();
        List<Message> content = messagePage.getContent();
        for (int i = content.size() - 1; i >= 0; i--) {
            messages.add(messageResponseMapper.toResponse(content.get(i), chatId));
        }
        return messages;
    }

    // --- Helpers ---

    private boolean isParticipant(PrivateChat chat, User user) {
        return chat.getUser1().getId().equals(user.getId())
                || chat.getUser2().getId().equals(user.getId());
    }

    private void notifyInvitation(PrivateChat chat, User target) {
        Map<String, Object> notify = buildChatPayload(chat, target);
        notify.put("status", "PENDING");
        notify.put("type", "INVITATION");
        notify.put("initiatorId", chat.getInitiator().getId());
        messagingTemplate.convertAndSendToUser(
                target.getId().toString(), "/queue/private", notify);
    }

    private void notifyStatusChange(PrivateChat chat, ChatStatus newStatus) {
        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("id", chat.getId());
        notify.put("type", "STATUS_CHANGE");
        notify.put("status", newStatus.name());
        // Push to both
        messagingTemplate.convertAndSendToUser(
                chat.getUser1().getId().toString(), "/queue/private", notify);
        messagingTemplate.convertAndSendToUser(
                chat.getUser2().getId().toString(), "/queue/private", notify);
    }

    private Map<String, Object> buildChatPayload(PrivateChat chat, User viewer) {
        User other = chat.getUser1().getId().equals(viewer.getId())
                ? chat.getUser2() : chat.getUser1();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", chat.getId());
        info.put("initiatorId", chat.getInitiator().getId());
        info.put("otherUser", Map.of(
                "id", other.getId(),
                "username", other.getUsername(),
                "nickname", other.getNickname()
        ));
        return info;
    }
}
