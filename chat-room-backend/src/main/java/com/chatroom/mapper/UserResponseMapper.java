package com.chatroom.mapper;

import com.chatroom.dto.response.CurrentUserResponse;
import com.chatroom.dto.response.UserSummaryResponse;
import com.chatroom.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {

    // 两个出口，字段差异就是可见性分级：
    //   toSummary（给别人看）：id/username/nickname/avatarUrl，不含 status 和 theme。
    //     status 不给是因为隐身用户对外必须显示为 OFFLINE，该转换由 PresenceService
    //     统一处理，这里若直接给真实状态会绕过隐身。
    //   toCurrentUser（给自己看）：多了 status 和 theme。
    // 两者都不含 password。
    //
    // toCurrentUser 里 theme 仍要兜底判空：实体的字段初始值只对"Java 新建的对象"生效，
    // 从数据库读出来的对象如果该列为 NULL，字段就是 null。

    public UserSummaryResponse toSummary(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl()
        );
    }

    public CurrentUserResponse toCurrentUser(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getStatus(),
                user.getTheme() != null ? user.getTheme() : "dark"
        );
    }
}
