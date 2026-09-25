package com.chatroom.enums;

/**
 * 消息类型。对应 messages.type。
 * SYSTEM 只允许服务端产生：客户端伪造 SYSTEM 会被 SendRequest 的字段相容性
 * 校验和 MessageService 拒绝。撤回后的消息对外也呈现为 SYSTEM。
 */
public enum MessageType {
    TEXT, IMAGE, FILE, SYSTEM
}
