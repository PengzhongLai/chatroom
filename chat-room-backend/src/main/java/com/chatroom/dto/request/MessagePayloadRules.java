package com.chatroom.dto.request;

import com.chatroom.enums.MessageType;

final class MessagePayloadRules {

    private MessagePayloadRules() {
    }

    static boolean isValid(MessageType type, String content, String fileName, String filePath) {
        return switch (type) {
            case TEXT -> content != null && !content.isBlank();
            case IMAGE -> hasFile(fileName, filePath) && filePath.matches(".*\\.(jpg|png|gif|webp)$");
            case FILE -> hasFile(fileName, filePath);
            case SYSTEM -> false;
        };
    }

    private static boolean hasFile(String fileName, String filePath) {
        return fileName != null && !fileName.isBlank()
                && filePath != null && !filePath.isBlank();
    }
}
