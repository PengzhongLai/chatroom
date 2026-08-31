package com.chatroom.dto.request;

import com.chatroom.enums.MessageType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PrivateMessageSendRequest(
        @NotNull(message = "私聊 ID 不能为空")
        @Positive(message = "私聊 ID 必须为正数")
        Long chatId,

        @Size(max = 4000, message = "消息内容不能超过 4000 个字符")
        String content,

        MessageType type,

        @Size(max = 255, message = "文件名不能超过 255 个字符")
        String fileName,

        @Size(max = 500, message = "文件路径不能超过 500 个字符")
        @Pattern(regexp = AttachmentPath.PATTERN, message = "文件路径格式不正确")
        String filePath
) {
    public MessageType resolvedType() {
        return type == null ? MessageType.TEXT : type;
    }

    @AssertTrue(message = "消息类型与内容不匹配，且客户端不能发送系统消息")
    public boolean isPayloadValid() {
        return MessagePayloadRules.isValid(resolvedType(), content, fileName, filePath);
    }
}
