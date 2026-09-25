package com.chatroom.enums;

/**
 * 频道成员角色。这里声明的顺序是业务权限顺序（高→低），
 * 与 MySQL 里 enum 按字母排序后的显示顺序不同，两者不一致是正常的：
 * 实体使用 EnumType.STRING，数据库存的是名字而不是序号。
 *
 * 注意：真正的所有权权威是 channels.creator_id，本枚举只是镜像。
 * 判断"是不是创建者"必须比较 creator_id，不能看 role 是否为 CREATOR
 * （历史数据可能残留与 creator_id 矛盾的 CREATOR 角色，见 V2 迁移脚本）。
 */
public enum MemberRole {
    CREATOR, ADMIN, MEMBER
}
