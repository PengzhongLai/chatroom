package com.chatroom.mapper;

import com.chatroom.dto.response.CurrentUserResponse;
import com.chatroom.dto.response.UserSummaryResponse;
import com.chatroom.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {

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
