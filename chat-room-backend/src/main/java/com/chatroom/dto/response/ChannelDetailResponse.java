package com.chatroom.dto.response;

import java.time.LocalDateTime;

// record：只读数据包（编译期生成全参构造器与 id()/name() 形式的访问器，没有 get 前缀）。
// DTO 用 record、实体用 class，差别在于 JPA 需要无参构造器与 setter，而 record 不可变。
//
// creator 用 UserSummaryResponse 而不是 User 实体：避免 password 外泄，也避免实体新增
// 字段时自动出现在接口响应里。
// isPublic/isMuted 用基本类型 boolean：响应里这两个字段必须有值，不接受 null。
// inviteCode 可能为 null（调用者无权限时由 Mapper 置空）。
public record ChannelDetailResponse(
        Long id,
        String name,
        String description,
        UserSummaryResponse creator,
        boolean isPublic,
        String inviteCode,
        boolean isMuted,
        LocalDateTime createdAt
) {
}
