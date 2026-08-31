package com.chatroom.dto.response;

public record FileUploadResponse(
        String fileName,
        String filePath,
        String fileType
) {
}
