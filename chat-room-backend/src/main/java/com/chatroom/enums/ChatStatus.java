package com.chatroom.enums;

/**
 * 私聊关系的状态。对应 private_chats.status。
 */
public enum ChatStatus {
    PENDING,   // 已发起申请，等对方同意
    ACTIVE,    // 双方已同意，可以互发消息
    REJECTED,  // 对方拒绝了申请
    DELETED    // 会话已删除，消息记录同时被清除
}
