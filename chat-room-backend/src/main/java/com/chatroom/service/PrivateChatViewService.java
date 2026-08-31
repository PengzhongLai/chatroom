package com.chatroom.service;

import com.chatroom.dto.response.PrivateChatResponse;
import com.chatroom.entity.PrivateChat;
import com.chatroom.entity.User;
import com.chatroom.mapper.PrivateChatResponseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateChatViewService {

    private final PrivateChatService privateChatService;
    private final UserService userService;
    private final PrivateChatResponseMapper privateChatResponseMapper;

    public PrivateChatViewService(
            PrivateChatService privateChatService,
            UserService userService,
            PrivateChatResponseMapper privateChatResponseMapper
    ) {
        this.privateChatService = privateChatService;
        this.userService = userService;
        this.privateChatResponseMapper = privateChatResponseMapper;
    }

    @Transactional
    public PrivateChatResponse initiate(Long targetUserId) {
        PrivateChat chat = privateChatService.initiateChat(targetUserId);
        User viewer = userService.getCurrentUser();
        return privateChatResponseMapper.toResponse(chat, viewer);
    }

    @Transactional
    public PrivateChatResponse accept(Long chatId) {
        PrivateChat chat = privateChatService.acceptChat(chatId);
        User viewer = userService.getCurrentUser();
        return privateChatResponseMapper.toResponse(chat, viewer);
    }
}
