package com.chatroom.enums;

/**
 * 消息类型。对应 messages.type。
 */
public enum MessageType {
    TEXT,     // 纯文本消息，内容存在 messages.content
    IMAGE,    // 图片消息，文件信息存在 file_name / file_path
    FILE,     // 附件消息，文件信息存在 file_name / file_path
    SYSTEM    // 系统消息，只能由服务端产生，客户端发送会被拒绝
}
