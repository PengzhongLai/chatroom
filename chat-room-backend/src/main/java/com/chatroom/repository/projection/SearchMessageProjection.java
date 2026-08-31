package com.chatroom.repository.projection;

import java.time.LocalDateTime;

public interface SearchMessageProjection {
    Long getId();
    Long getChannelId();
    Long getChatId();
    Long getSenderId();
    String getSenderUsername();
    String getSenderNickname();
    String getType();
    String getContent();
    String getFileName();
    String getFilePath();
    Boolean getIsRecalled();
    LocalDateTime getCreatedAt();
    String getContext();
    Long getContextId();
    String getContextName();
}
