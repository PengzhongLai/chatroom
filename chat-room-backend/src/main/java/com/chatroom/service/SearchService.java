package com.chatroom.service;

import com.chatroom.dto.response.SearchMessageResponse;
import com.chatroom.exception.BusinessException;
import com.chatroom.repository.MessageRepository;
import com.chatroom.repository.projection.SearchMessageProjection;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chatroom.validation.PaginationPolicy;

import java.util.List;

@Service
public class SearchService {

    private static final int MAX_QUERY_LENGTH = 100;

    private final MessageRepository messageRepository;

    public SearchService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public List<SearchMessageResponse> searchMessages(String query, int page, int size) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            throw BusinessException.badRequest("搜索关键词不能为空");
        }
        if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
            throw BusinessException.badRequest("搜索关键词不能超过 100 个字符");
        }
        PaginationPolicy.validate(page, size);

        Long currentUserId = currentUserId();
        String escapedKeyword = escapeLikePattern(normalizedQuery);
        return messageRepository.searchAccessible(
                        currentUserId,
                        escapedKeyword,
                        PageRequest.of(page, size)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private SearchMessageResponse toResponse(SearchMessageProjection message) {
        return new SearchMessageResponse(
                message.getId(),
                message.getChannelId(),
                message.getChatId(),
                new SearchMessageResponse.Sender(
                        message.getSenderId(),
                        message.getSenderUsername(),
                        message.getSenderNickname()
                ),
                message.getType(),
                message.getContent(),
                message.getFileName(),
                message.getFilePath(),
                Boolean.TRUE.equals(message.getIsRecalled()),
                message.getCreatedAt(),
                message.getContext(),
                message.getContextId(),
                message.getContextName()
        );
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw BusinessException.unauthorized("用户未登录");
        }
        return userId;
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
