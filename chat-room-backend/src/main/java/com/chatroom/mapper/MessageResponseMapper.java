package com.chatroom.mapper;

import com.chatroom.dto.response.MentionResponse;
import com.chatroom.dto.response.MessageResponse;
import com.chatroom.entity.Message;
import com.chatroom.entity.User;
import com.chatroom.enums.MessageType;
import com.chatroom.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MessageResponseMapper {

    private final UserRepository userRepository;
    private final UserResponseMapper userResponseMapper;

    public MessageResponseMapper(UserRepository userRepository, UserResponseMapper userResponseMapper) {
        this.userRepository = userRepository;
        this.userResponseMapper = userResponseMapper;
    }

    public MessageResponse toResponse(Message message) {
        Long chatId = message.getPrivateChat() != null ? message.getPrivateChat().getId() : null;
        return toResponse(message, chatId);
    }

    public MessageResponse toResponse(Message message, Long chatId) {
        boolean recalled = Boolean.TRUE.equals(message.getIsRecalled());
        Long channelId = message.getChannel() != null ? message.getChannel().getId() : null;
        return new MessageResponse(
                message.getId(),
                channelId,
                chatId,
                userResponseMapper.toSummary(message.getSender()),
                recalled ? MessageType.SYSTEM : message.getType(),
                recalled ? null : message.getContent(),
                recalled ? null : message.getFileName(),
                recalled ? null : message.getFilePath(),
                recalled,
                message.getCreatedAt(),
                recalled ? List.of() : extractMentions(message.getContent(), message.getSender())
        );
    }

    public Map<String, Object> toPayload(Message message) {
        MessageResponse response = toResponse(message);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", response.id());
        payload.put("channelId", response.channelId());
        if (response.chatId() != null) {
            payload.put("chatId", response.chatId());
        }
        payload.put("sender", response.sender());
        payload.put("type", response.type().name());
        payload.put("content", response.content());
        payload.put("fileName", response.fileName());
        payload.put("filePath", response.filePath());
        payload.put("isRecalled", response.isRecalled());
        payload.put("createdAt", response.createdAt().toString());
        if (!response.mentions().isEmpty()) {
            payload.put("mentions", response.mentions());
        }
        return payload;
    }

    private List<MentionResponse> extractMentions(String content, User sender) {
        List<MentionResponse> result = new ArrayList<>();
        if (content == null) {
            return result;
        }

        int index = 0;
        while ((index = content.indexOf('@', index)) != -1) {
            int end = index + 1;
            while (end < content.length() && Character.isLetterOrDigit(content.charAt(end))) {
                end++;
            }
            if (end > index + 1) {
                String username = content.substring(index + 1, end);
                Optional<User> target = userRepository.findByUsername(username);
                if (target.isPresent() && (sender == null || !target.get().getId().equals(sender.getId()))) {
                    User mentioned = target.get();
                    result.add(new MentionResponse(
                            mentioned.getId(),
                            mentioned.getUsername(),
                            mentioned.getNickname()
                    ));
                }
            }
            index = end;
        }
        return result;
    }
}
