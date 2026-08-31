package com.chatroom.dto.request;

import com.chatroom.enums.HistoryLevel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChannelInviteRequest(
        @NotNull(message = "用户 ID 不能为空")
        @Positive(message = "用户 ID 必须为正数")
        Long userId,

        HistoryLevel historyLevel,

        @Min(value = 1, message = "历史消息条数不能小于 1")
        @Max(value = 1000, message = "历史消息条数不能超过 1000")
        Integer historyLimit
) {
    public HistoryLevel resolvedHistoryLevel() {
        return historyLevel == null ? HistoryLevel.ALL : historyLevel;
    }

    public Integer resolvedHistoryLimit() {
        return resolvedHistoryLevel() == HistoryLevel.LIMITED ? historyLimit : null;
    }

    @AssertTrue(message = "LIMITED 模式必须提供 1 到 1000 的历史消息条数")
    public boolean isHistoryLimitValid() {
        return resolvedHistoryLevel() != HistoryLevel.LIMITED || historyLimit != null;
    }
}
