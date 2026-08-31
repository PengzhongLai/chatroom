package com.chatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ThemeUpdateRequest(
        @NotBlank(message = "主题不能为空")
        @Pattern(regexp = "dark|light", message = "主题仅支持 dark 或 light")
        String theme
) {
}
