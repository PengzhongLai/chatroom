package com.chatroom.enums;

/**
 * 频道成员角色，决定该成员在频道里的操作权限。对应 channel_members.role。
 * 权限从高到低：CREATOR > ADMIN > MEMBER。
 * 注意：频道所有权的权威记录是 channels.creator_id，本角色只是镜像。
 */
public enum MemberRole {
    CREATOR,   // 创建者：可转让、解散频道，可踢出任何人
    ADMIN,     // 管理员：可邀请、踢出普通成员、禁言
    MEMBER     // 普通成员：只能发消息、查看历史
}
