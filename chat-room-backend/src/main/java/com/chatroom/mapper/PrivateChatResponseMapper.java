package com.chatroom.mapper;

import com.chatroom.dto.response.PrivateChatResponse;
import com.chatroom.entity.Message;
import com.chatroom.entity.PrivateChat;
import com.chatroom.entity.User;
import org.springframework.stereotype.Component;

@Component
public class PrivateChatResponseMapper {

    private final UserResponseMapper userResponseMapper;

    public PrivateChatResponseMapper(UserResponseMapper userResponseMapper) {
        this.userResponseMapper = userResponseMapper;
    }

    public PrivateChatResponse toResponse(PrivateChat chat, User viewer) {
        return toResponse(chat, viewer, null);
    }

    public PrivateChatResponse toResponse(PrivateChat chat, User viewer, Message lastMessage) {
        User other = chat.getUser1().getId().equals(viewer.getId())
                ? chat.getUser2()
                : chat.getUser1();
        boolean recalled = lastMessage != null && Boolean.TRUE.equals(lastMessage.getIsRecalled());
        return new PrivateChatResponse(
                chat.getId(),
                chat.getInitiator().getId(),
                chat.getInitiator().getId().equals(viewer.getId()),
                chat.getStatus(),
                userResponseMapper.toSummary(other),
                lastMessage == null ? null : recalled ? "消息已撤回" : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                lastMessage == null ? null : lastMessage.getSender().getId()
        );
    }
}
