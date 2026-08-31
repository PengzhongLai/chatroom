package com.chatroom.dto.request;

final class AttachmentPath {

    static final String PATTERN = "^/files/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\\.(jpg|png|gif|webp|pdf|doc|docx|txt|zip|rar|7z)$";

    private AttachmentPath() {
    }
}
