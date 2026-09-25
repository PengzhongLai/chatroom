package com.chatroom.enums;

/**
 * 私聊关系的状态机。对应 private_chats.status。
 * 新关系创建时实体默认 PENDING；接收方反向发起申请会直接置为 ACTIVE
 * （视为双方都同意），deleteChat 置 DELETED 并清除该会话的消息记录。
 */
public enum ChatStatus {
    PENDING, ACTIVE, REJECTED, DELETED
}
