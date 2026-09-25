package com.chatroom.mapper;

import com.chatroom.dto.response.CurrentUserResponse;
import com.chatroom.dto.response.UserSummaryResponse;
import com.chatroom.entity.User;
import org.springframework.stereotype.Component;

/**
 * 把 User 实体转换成两种响应形态：面向他人的摘要、面向本人的完整信息。
 */
@Component
public class UserResponseMapper {

    /** 转成用户摘要，给别人看。只含 ID、用户名、昵称、头像，不含在线状态和主题 */
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

    /** 转成当前用户信息，给本人看。比摘要多了在线状态和主题偏好 */
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
